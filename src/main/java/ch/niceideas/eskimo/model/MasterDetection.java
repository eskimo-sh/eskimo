package ch.niceideas.eskimo.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.text.SimpleDateFormat;
import java.util.regex.Pattern;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class MasterDetection {

    private MasterDetectionStrategy detectionStrategy;
    private String logFile;
    private String grep;
    private Pattern timeStampExtractRexp;
    private SimpleDateFormat timeStampFormat;
}
