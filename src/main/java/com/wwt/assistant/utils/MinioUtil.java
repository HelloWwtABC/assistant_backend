package com.wwt.assistant.utils;

import com.wwt.assistant.common.UserContextHolder;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.errors.ErrorResponseException;
import io.minio.http.Method;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Component
@RequiredArgsConstructor
public class MinioUtil {

    private static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";
    private static final int DEFAULT_PRESIGNED_URL_EXPIRY_SECONDS = 60 * 60;

    private final MinioClient minioClient;

    @Value("${minio.endpoint}")
    private String endpoint;

    @Value("${minio.bucket-name}")
    private String bucketName;

    @Value("${minio.file-size}")
    private DataSize maxFileSize;

    public String upload(MultipartFile file) {
        return upload(file, null);
    }

    public String upload(MultipartFile file, String objectName) {
        Assert.notNull(file, "file must not be null");
        Assert.isTrue(!file.isEmpty(), "file must not be empty");
        validateFileSize(file.getSize());

        String finalObjectName = buildTeamScopedObjectName(buildObjectName(objectName, file.getOriginalFilename()));
        String contentType = StringUtils.hasText(file.getContentType()) ? file.getContentType() : DEFAULT_CONTENT_TYPE;
        try (InputStream inputStream = file.getInputStream()) {
            putObject(finalObjectName, inputStream, file.getSize(), contentType);
            return finalObjectName;
        } catch (Exception ex) {
            throw new IllegalStateException("上传文件到 MinIO 失败", ex);
        }
    }

    public String upload(InputStream inputStream, long objectSize, String objectName, String contentType) {
        Assert.notNull(inputStream, "inputStream must not be null");
        Assert.isTrue(objectSize >= 0, "objectSize must not be negative");
        Assert.hasText(objectName, "objectName must not be blank");
        validateFileSize(objectSize);

        String finalObjectName = buildTeamScopedObjectName(objectName.trim());
        String finalContentType = StringUtils.hasText(contentType) ? contentType : DEFAULT_CONTENT_TYPE;
        try {
            putObject(finalObjectName, inputStream, objectSize, finalContentType);
            return finalObjectName;
        } catch (Exception ex) {
            throw new IllegalStateException("上传流到 MinIO 失败", ex);
        }
    }

    public InputStream getObject(String objectName) {
        Assert.hasText(objectName, "objectName must not be blank");
        try {
            return minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName.trim())
                            .build());
        } catch (Exception ex) {
            throw new IllegalStateException("从 MinIO 读取文件失败", ex);
        }
    }

    public void remove(String objectName) {
        Assert.hasText(objectName, "objectName must not be blank");
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName.trim())
                            .build());
        } catch (Exception ex) {
            throw new IllegalStateException("删除 MinIO 文件失败", ex);
        }
    }

    /**
     * 判断 MinIO 中某个文件【是否存在】
     * @param objectName
     * @return
     */
    public boolean exists(String objectName) {
        Assert.hasText(objectName, "objectName must not be blank");
        try {
            minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName.trim())
                            .build());
            return true;
        } catch (ErrorResponseException ex) {
            if (ex.errorResponse() != null && Objects.equals(ex.errorResponse().code(), "NoSuchKey")) {
                return false;
            }
            throw new IllegalStateException("查询 MinIO 文件状态失败", ex);
        } catch (Exception ex) {
            throw new IllegalStateException("查询 MinIO 文件状态失败", ex);
        }
    }

    /**
     * 获取 MinIO 中某个文件的【详细信息】
     * @param objectName
     * @return
     */
    public StatObjectResponse statObject(String objectName) {
        Assert.hasText(objectName, "objectName must not be blank");
        try {
            return minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName.trim())
                            .build());
        } catch (Exception ex) {
            throw new IllegalStateException("获取 MinIO 文件信息失败", ex);
        }
    }

    public String getObjectUrl(String objectName) {
        Assert.hasText(objectName, "objectName must not be blank");
        String normalizedEndpoint = endpoint.endsWith("/") ? endpoint.substring(0, endpoint.length() - 1) : endpoint;
        String encodedObjectName = encodeObjectName(objectName.trim());
        return normalizedEndpoint + "/" + bucketName + "/" + encodedObjectName;
    }

    public String getPresignedUrl(String objectName) {
        return getPresignedUrl(objectName, DEFAULT_PRESIGNED_URL_EXPIRY_SECONDS);
    }

    /**
     * 生成 MinIO 临时访问链接（预签名URL）
     * @param objectName
     * @param expirySeconds
     * @return
     */
    public String getPresignedUrl(String objectName, int expirySeconds) {
        Assert.hasText(objectName, "objectName must not be blank");
        Assert.isTrue(expirySeconds > 0, "expirySeconds must be greater than 0");
        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(bucketName)
                            .object(objectName.trim())
                            .expiry(expirySeconds)
                            .build());
        } catch (Exception ex) {
            throw new IllegalStateException("生成 MinIO 预签名地址失败", ex);
        }
    }

    /**
     * 存入容器
     * @param objectName
     * @param inputStream
     * @param objectSize
     * @param contentType
     * @throws Exception
     */
    private void putObject(String objectName, InputStream inputStream, long objectSize, String contentType) throws Exception {
        ensureBucketExists();
        minioClient.putObject(
                PutObjectArgs.builder()
                        .bucket(bucketName)
                        .object(objectName)
                        .stream(inputStream, objectSize, -1)
                        .contentType(contentType)
                        .build());
        log.debug("Uploaded object to MinIO, bucket={}, objectName={}", bucketName, objectName);
    }

    /**
     * 校验容器存在
     * @throws Exception
     */
    private void ensureBucketExists() throws Exception {
        boolean bucketExists = minioClient.bucketExists(
                io.minio.BucketExistsArgs.builder()
                        .bucket(bucketName)
                        .build());
        if (!bucketExists) {
            minioClient.makeBucket(
                    MakeBucketArgs.builder()
                            .bucket(bucketName)
                            .build());
            log.info("Created MinIO bucket: {}", bucketName);
        }
    }

    /**
     * 验证文件大小
     * @param fileSize
     */
    private void validateFileSize(long fileSize) {
        Assert.isTrue(fileSize >= 0, "fileSize must not be negative");
        if (maxFileSize != null && fileSize > maxFileSize.toBytes()) {
            throw new IllegalArgumentException("文件大小超过 MinIO 配置限制");
        }
    }

    /**
     * 为文件统一名称格式
     * @param objectName
     * @param originalFilename
     * @return
     */
    private String buildObjectName(String objectName, String originalFilename) {
        if (StringUtils.hasText(objectName)) {
            return objectName.trim();
        }

        String extension = "";
        if (StringUtils.hasText(originalFilename) && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf('.'));
        }
        return UUID.randomUUID().toString().replace("-", "") + extension;
    }

    /**
     * 以teamID 进行分类存储
     * @param objectName
     * @return
     */
    private String buildTeamScopedObjectName(String objectName) {
        Assert.hasText(objectName, "objectName must not be blank");
        Long teamId = UserContextHolder.getTeamId();
        if (teamId == null) {
            throw new IllegalStateException("当前上下文缺少 teamId，无法按团队目录存储文件");
        }

        String normalizedObjectName = objectName.trim();
        String teamPrefix = teamId + "/";
        return normalizedObjectName.startsWith(teamPrefix) ? normalizedObjectName : teamPrefix + normalizedObjectName;
    }

    private String encodeObjectName(String objectName) {
        String[] segments = objectName.split("/");
        StringBuilder encoded = new StringBuilder();
        for (int i = 0; i < segments.length; i++) {
            if (i > 0) {
                encoded.append('/');
            }
            encoded.append(URLEncoder.encode(segments[i], StandardCharsets.UTF_8));
        }
        return encoded.toString();
    }
}
