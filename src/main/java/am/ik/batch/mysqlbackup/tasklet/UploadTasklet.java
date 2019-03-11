package am.ik.batch.mysqlbackup.tasklet;

import am.ik.batch.mysqlbackup.BackupProps;
import io.minio.MinioClient;
import io.minio.Result;
import io.minio.messages.Item;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.util.FileSystemUtils;

import java.io.File;
import java.time.Clock;
import java.time.LocalDate;

public class UploadTasklet implements Tasklet {

    private static final Logger log = LoggerFactory.getLogger(UploadTasklet.class);

    private final MinioClient minioClient;

    private final Clock clock;

    private final BackupProps props;

    public UploadTasklet(MinioClient minioClient, Clock clock, BackupProps props) {
        this.minioClient = minioClient;
        this.clock = clock;
        this.props = props;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        BackupProps.S3 s3 = this.props.getS3();
        if (!this.minioClient.bucketExists(s3.getBucket())) {
            log.info("Create bucket ({}/{})", s3.getBucket(), s3.getRegion());
            this.minioClient.makeBucket(s3.getBucket(), s3.getRegion());
        }

        Iterable<Result<Item>> objects = this.minioClient.listObjects(s3.getBucket());
        LocalDate now = LocalDate.now(this.clock);
        for (Result<Item> object : objects) {
            Item item = object.get();
            String objectName = item.objectName();
            LocalDate backupDate = LocalDate.parse(new File(objectName).getParent());
            if (backupDate.isBefore(now.minusDays(this.props.getRetention().toDays()))) {
                log.info("Deleting {} ...", backupDate);
                this.minioClient.removeObject(s3.getBucket(), objectName);
            }
        }

        for (String name : this.props.getTargets().keySet()) {
            String fileName = new File(name + ".sql").getAbsolutePath();
            String objectName = now + "/" + name + ".sql";
            log.info("Uploading {} to {} ...", fileName, objectName);
            this.minioClient.putObject(s3.getBucket(), objectName, fileName);
            FileSystemUtils.deleteRecursively(new File(name + ".sql"));
        }
        return RepeatStatus.FINISHED;
    }
}
