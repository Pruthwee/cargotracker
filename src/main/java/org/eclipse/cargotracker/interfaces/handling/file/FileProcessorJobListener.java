import java.time.LocalDateTime;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import jakarta.inject.Named;

@Dependent
@Named("FileProcessorJobListener")
public class FileProcessorJobListener implements JobListener {

  @Inject private Logger logger;
    logger.log(Level.INFO, "Handling event file processor batch job starting at {0}", LocalDateTime.now(java.time.Clock.systemUTC()));
    logger.log(Level.INFO, "Handling event file processor batch job completed at {0}", LocalDateTime.now(java.time.Clock.systemUTC()));
