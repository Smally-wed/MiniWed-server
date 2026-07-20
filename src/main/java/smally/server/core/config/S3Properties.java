package smally.server.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "aws.s3")
public class S3Properties {

    private String bucket;
    private String region;
    private long presignedTtlSeconds;
    private long maxSizeBytes;

    public String getBucket() { return bucket; }
    public void setBucket(String bucket) { this.bucket = bucket; }

    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }

    public long getPresignedTtlSeconds() { return presignedTtlSeconds; }
    public void setPresignedTtlSeconds(long presignedTtlSeconds) { this.presignedTtlSeconds = presignedTtlSeconds; }

    public long getMaxSizeBytes() { return maxSizeBytes; }
    public void setMaxSizeBytes(long maxSizeBytes) { this.maxSizeBytes = maxSizeBytes; }
}
