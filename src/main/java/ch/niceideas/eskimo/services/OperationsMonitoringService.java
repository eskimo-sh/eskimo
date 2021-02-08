package ch.niceideas.eskimo.services;

import ch.niceideas.eskimo.model.JSONOpCommand;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_SINGLETON)
public class OperationsMonitoringService {

    @Autowired
    private MessagingService messagingService;

    @Autowired
    private NotificationService notificationService;

    private final ReentrantLock systemActionLock = new ReentrantLock();
    private final AtomicBoolean interruption = new AtomicBoolean(false);
    private final AtomicBoolean interruptionNotified = new AtomicBoolean(false);
    private boolean lastOperationSuccess;
    private JSONOpCommand currentOperation = null;

    void setMessagingService(MessagingService messagingService) {
        this.messagingService = messagingService;
    }
    void setNotificationService(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    public boolean isProcessingPending() {
        return systemActionLock.isLocked();
    }

    void operationsStarted(JSONOpCommand operation) {
        currentOperation = operation;
        systemActionLock.lock();
    }

    void operationsFinished(boolean success) {
        setLastOperationSuccess(success);
        systemActionLock.unlock();
        interruption.set(false);
        interruptionNotified.set(false);
        currentOperation = null;
    }

    public void interruptProcessing() {
        if (isProcessingPending()) {
            interruption.set(true);
        }
    }

    boolean isInterrupted () {
        notifyInterruption();
        return interruption.get();
    }

    void notifyInterruption() {
        if (interruption.get() && !interruptionNotified.get()) {
            notificationService.addError("Processing has been interrupted");
            messagingService.addLine("Processing has been interrupted");
            interruptionNotified.set(true);
        }
    }

    public boolean getLastOperationSuccess() {
        return lastOperationSuccess;
    }

    private void setLastOperationSuccess(boolean success) {
        lastOperationSuccess = success;
    }
}
