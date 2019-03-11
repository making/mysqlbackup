package am.ik.batch.mysqlbackup.tasklet;

import am.ik.batch.mysqlbackup.BackupProps;
import am.ik.batch.mysqlbackup.CommandExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;

public class DumpTasklet implements Tasklet {

    private static final Logger log = LoggerFactory.getLogger(DumpTasklet.class);

    private final BackupProps props;

    public DumpTasklet(BackupProps props) {
        this.props = props;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        this.props.getTargets().forEach((name, target) -> {
            log.info("Dumping {} ...", name);
            CommandExecutor commandExecutor = new CommandExecutor(this.outputStream(name), System.err);
            commandExecutor.exec(target.mysqldumpCommand());
        });
        return RepeatStatus.FINISHED;
    }

    OutputStream outputStream(String name) {
        try {
            return new FileOutputStream(new File(name + ".sql"));
        } catch (FileNotFoundException e) {
            throw new UncheckedIOException(e);
        }
    }
}
