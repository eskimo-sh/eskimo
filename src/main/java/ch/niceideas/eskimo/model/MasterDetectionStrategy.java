package ch.niceideas.eskimo.model;

import ch.niceideas.eskimo.services.mdStrategy.LogFileStrategy;
import ch.niceideas.eskimo.services.mdStrategy.MdStrategy;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;

@AllArgsConstructor
public enum MasterDetectionStrategy {

    LOG_FILE(new LogFileStrategy());

    @Getter
    private MdStrategy strategy;
}
