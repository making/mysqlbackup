package am.ik.batch.mysqlbackup;

import am.ik.batch.mysqlbackup.tasklet.DumpTasklet;
import am.ik.batch.mysqlbackup.tasklet.UploadTasklet;
import io.minio.MinioClient;
import io.minio.errors.InvalidEndpointException;
import io.minio.errors.InvalidPortException;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.configuration.annotation.JobBuilderFactory;
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
@EnableBatchProcessing
public class JobConfig {

    private final JobBuilderFactory jobBuilderFactory;

    private final StepBuilderFactory stepBuilderFactory;

    private final BackupProps props;

    public JobConfig(JobBuilderFactory jobBuilderFactory, StepBuilderFactory stepBuilderFactory, BackupProps props) {
        this.jobBuilderFactory = jobBuilderFactory;
        this.stepBuilderFactory = stepBuilderFactory;
        this.props = props;
    }

    @Bean
    public Step dumpStep() {
        try {
            return this.stepBuilderFactory.get("dump") //
                .tasklet(new DumpTasklet(this.props))
                .build();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Bean
    public Step uploadStep() {
        return this.stepBuilderFactory.get("upload") //
            .tasklet(new UploadTasklet(minioClient(), clock(), this.props)).build();
    }

    @Bean
    public Job backupJob() {
        return this.jobBuilderFactory.get("backup") //
            .incrementer(new RunIdIncrementer()) //
            .start(dumpStep())
            .next(uploadStep())
            .build();
    }

    @Bean
    public MinioClient minioClient() {
        BackupProps.S3 s3 = props.getS3();
        try {
            return new MinioClient(s3.getEndpoint(), s3.getAccessKey(), s3.getSecretKey(), s3.getRegion());
        } catch (InvalidEndpointException | InvalidPortException e) {
            throw new IllegalArgumentException(e);
        }
    }

    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
