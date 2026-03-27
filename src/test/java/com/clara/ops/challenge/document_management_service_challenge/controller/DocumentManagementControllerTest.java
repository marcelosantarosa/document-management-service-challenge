package com.clara.ops.challenge.document_management_service_challenge.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.clara.ops.challenge.document_management_service_challenge.controller.response.DocumentDownloadUrlResponse;
import com.clara.ops.challenge.document_management_service_challenge.controller.response.DocumentResponse;
import com.clara.ops.challenge.document_management_service_challenge.controller.response.MetadataResponse;
import com.clara.ops.challenge.document_management_service_challenge.controller.response.PaginatedDocumentSearchResponse;
import com.clara.ops.challenge.document_management_service_challenge.exception.BusinessException;
import com.clara.ops.challenge.document_management_service_challenge.service.DocumentService;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = DocumentManagementController.class)
public class DocumentManagementControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private DocumentService service;

  @Test
  void uploadDocument_shouldReturn201_whenPayloadIsValid() throws Exception {
    MockMultipartFile file =
        new MockMultipartFile(
            "file", "contract.pdf", "application/pdf", "fake-pdf".getBytes(StandardCharsets.UTF_8));

    String metadataJson =
        """
        {"user":"marcelo","name":"contract.pdf","tags":["legal","finance"]}
        """;
    MockMultipartFile metadata =
        new MockMultipartFile(
            "metadata",
            "",
            MediaType.TEXT_PLAIN_VALUE,
            metadataJson.getBytes(StandardCharsets.UTF_8));

    mockMvc
        .perform(multipart("/document-management/upload").file(file).file(metadata))
        .andExpect(status().isCreated());

    verify(service).uploadDocument(any(), any());
  }

  @Test
  void uploadDocument_shouldReturn400_whenMetadataJsonIsInvalid() throws Exception {
    MockMultipartFile file =
        new MockMultipartFile(
            "file", "contract.pdf", "application/pdf", "fake-pdf".getBytes(StandardCharsets.UTF_8));

    String invalidJson = "{\"user\":\"marcelo\",\"name\":";
    MockMultipartFile metadata =
        new MockMultipartFile(
            "metadata",
            "",
            MediaType.TEXT_PLAIN_VALUE,
            invalidJson.getBytes(StandardCharsets.UTF_8));

    mockMvc
        .perform(multipart("/document-management/upload").file(file).file(metadata))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.statusCode").value(400))
        .andExpect(jsonPath("$.message").value("Invalid JSON format for metadata"))
        .andExpect(jsonPath("$.path").value("/document-management/upload"));
  }

  @Test
  void uploadDocument_shouldReturn400_whenBeanValidationFails() throws Exception {
    MockMultipartFile file =
        new MockMultipartFile(
            "file", "contract.pdf", "application/pdf", "fake-pdf".getBytes(StandardCharsets.UTF_8));

    String invalidMetadata =
        """
        {"user":"","name":"contract.pdf","tags":["legal"]}
        """;
    MockMultipartFile metadata =
        new MockMultipartFile(
            "metadata",
            "",
            MediaType.TEXT_PLAIN_VALUE,
            invalidMetadata.getBytes(StandardCharsets.UTF_8));

    mockMvc
        .perform(multipart("/document-management/upload").file(file).file(metadata))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.statusCode").value(400))
        .andExpect(jsonPath("$.message").value("Required fields are empty or null."))
        .andExpect(jsonPath("$.path").value("/document-management/upload"))
        .andExpect(jsonPath("$.errors[0]", containsString("The user is required.")));
  }

  @Test
  void searchDocument_shouldReturn200AndResponseBody() throws Exception {
    PaginatedDocumentSearchResponse response =
        PaginatedDocumentSearchResponse.builder()
            .metadata(
                MetadataResponse.builder()
                    .currentPage(1)
                    .itemsPerPage(5)
                    .currentItems(1)
                    .totalPages(3)
                    .totalItems(11)
                    .build())
            .document(
                List.of(
                    DocumentResponse.builder()
                        .id("10")
                        .user("marcelo")
                        .name("contract.pdf")
                        .tags(List.of("legal", "finance"))
                        .size(BigInteger.valueOf(2048))
                        .type("application/pdf")
                        .createdAt("2026-03-26T10:15:30Z")
                        .build()))
            .build();

    when(service.searchDocuments(eq(1), eq(5), eq(List.of("createdAt")), any()))
        .thenReturn(response);

    String body =
        """
        {"user":"marcelo","name":"contract","tags":["legal"]}
        """;

    mockMvc
        .perform(
            post("/document-management/search")
                .param("page", "1")
                .param("size", "5")
                .param("sort", "createdAt")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.metadata.currentPage").value(1))
        .andExpect(jsonPath("$.metadata.itemsPerPage").value(5))
        .andExpect(jsonPath("$.document[0].id").value("10"))
        .andExpect(jsonPath("$.document[0].user").value("marcelo"))
        .andExpect(jsonPath("$.document[0].name").value("contract.pdf"));
  }

  @Test
  void download_shouldReturn200AndUrl() throws Exception {
    when(service.getDocumentDownloadUrl(99))
        .thenReturn(new DocumentDownloadUrlResponse("https://minio.local/signed-url"));

    mockMvc
        .perform(get("/document-management/download/99"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.url").value("https://minio.local/signed-url"));
  }

  @Test
  void download_shouldReturn400_whenServiceThrowsBusinessException() throws Exception {
    when(service.getDocumentDownloadUrl(123))
        .thenThrow(new BusinessException("Document not found"));

    mockMvc
        .perform(get("/document-management/download/123"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.statusCode").value(400))
        .andExpect(jsonPath("$.message").value("Document not found"))
        .andExpect(jsonPath("$.path").value("/document-management/download/123"));
  }
}
