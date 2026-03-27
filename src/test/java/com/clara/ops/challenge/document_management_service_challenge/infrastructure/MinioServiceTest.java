package com.clara.ops.challenge.document_management_service_challenge.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.clara.ops.challenge.document_management_service_challenge.exception.BusinessException;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.UploadObjectArgs;
import java.io.File;
import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

@ExtendWith(MockitoExtension.class)
public class MinioServiceTest {

  @Mock private MinioClient minioClient;

  @Mock private MultipartFile multipartFile;

  private MinioService service;

  @BeforeEach
  void setUp() {
    service = new MinioService(minioClient);
    ReflectionTestUtils.setField(service, "bucketName", "document-bucket");
  }

  @Test
  void init_shouldCreateBucket_whenBucketDoesNotExist() throws Exception {
    when(minioClient.bucketExists(any())).thenReturn(false);

    service.init();

    verify(minioClient).bucketExists(any());
    verify(minioClient).makeBucket(any());
  }

  @Test
  void init_shouldNotCreateBucket_whenBucketAlreadyExists() throws Exception {
    when(minioClient.bucketExists(any())).thenReturn(true);

    service.init();

    verify(minioClient).bucketExists(any());
    verify(minioClient, never()).makeBucket(any());
  }

  @Test
  void init_shouldThrowBusinessException_whenMinioFails() throws Exception {
    when(minioClient.bucketExists(any())).thenThrow(new RuntimeException("minio unavailable"));

    assertThatThrownBy(service::init)
        .isInstanceOf(BusinessException.class)
        .hasMessage("Error creating bucket document-bucket");
  }

  @Test
  void uploadFile_shouldUploadSuccessfully() throws Exception {
    when(multipartFile.getContentType()).thenReturn("application/pdf");

    doAnswer(
            invocation -> {
              File temp = invocation.getArgument(0, File.class);
              assertThat(temp).exists();
              assertThat(temp.getName()).startsWith("upload-");
              return null;
            })
        .when(multipartFile)
        .transferTo(any(File.class));

    service.uploadFile(multipartFile, "marcelo/contract.pdf");

    verify(multipartFile).transferTo(any(File.class));
    verify(minioClient).uploadObject(any(UploadObjectArgs.class));

    ArgumentCaptor<UploadObjectArgs> argsCaptor = ArgumentCaptor.forClass(UploadObjectArgs.class);
    verify(minioClient).uploadObject(argsCaptor.capture());
    assertThat(argsCaptor.getValue()).isNotNull();
  }

  @Test
  void uploadFile_shouldThrowBusinessException_whenTransferFails() throws Exception {
    doThrow(new IOException("disk error")).when(multipartFile).transferTo(any(File.class));

    assertThatThrownBy(() -> service.uploadFile(multipartFile, "marcelo/contract.pdf"))
        .isInstanceOf(BusinessException.class)
        .hasMessage("Error uploading file marcelo/contract.pdf");
  }

  @Test
  void uploadFile_shouldThrowBusinessException_whenMinioUploadFails() throws Exception {
    when(multipartFile.getContentType()).thenReturn("application/pdf");
    when(minioClient.uploadObject(any(UploadObjectArgs.class)))
        .thenThrow(new RuntimeException("minio upload error"));

    assertThatThrownBy(() -> service.uploadFile(multipartFile, "marcelo/contract.pdf"))
        .isInstanceOf(BusinessException.class)
        .hasMessage("Error uploading file marcelo/contract.pdf");
  }

  @Test
  void generatePresignedUrl_shouldReturnUrl() throws Exception {
    when(minioClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class)))
        .thenReturn("https://minio.local/signed-url");

    String result = service.generatePresignedUrl("marcelo/contract.pdf");

    assertThat(result).isEqualTo("https://minio.local/signed-url");
    verify(minioClient).getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class));
  }

  @Test
  void generatePresignedUrl_shouldThrowBusinessException_whenMinioFails() throws Exception {
    when(minioClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class)))
        .thenThrow(new RuntimeException("minio error"));

    assertThatThrownBy(() -> service.generatePresignedUrl("marcelo/contract.pdf"))
        .isInstanceOf(BusinessException.class)
        .hasMessage("Error generating presigned url for the file marcelo/contract.pdf");
  }
}
