package ch.niceideas.eskimo.model.service;

import ch.niceideas.eskimo.services.mdStrategy.LogFileStrategy;
import ch.niceideas.eskimo.services.mdStrategy.MdStrategy;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
public enum MasterDetectionStrategy {

    LOG_FILE(new LogFileStrategy());

    @Getter
    private MdStrategy strategy;
}
