package ch.niceideas.eskimo.model.service;

import ch.niceideas.eskimo.services.mdstrategy.LogFileStrategy;
import ch.niceideas.eskimo.services.mdstrategy.MdStrategy;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
public enum MasterDetectionStrategy {

    LOG_FILE(new LogFileStrategy());

    @Getter
    private MdStrategy strategy;
}
