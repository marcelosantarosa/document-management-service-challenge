package com.clara.ops.challenge.document_management_service_challenge.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.clara.ops.challenge.document_management_service_challenge.controller.request.DocumentSearchFiltersRequest;
import com.clara.ops.challenge.document_management_service_challenge.controller.request.UploadDocumentRequest;
import com.clara.ops.challenge.document_management_service_challenge.controller.response.DocumentDownloadUrlResponse;
import com.clara.ops.challenge.document_management_service_challenge.controller.response.DocumentResponse;
import com.clara.ops.challenge.document_management_service_challenge.controller.response.MetadataResponse;
import com.clara.ops.challenge.document_management_service_challenge.controller.response.PaginatedDocumentSearchResponse;
import com.clara.ops.challenge.document_management_service_challenge.domain.entity.Document;
import com.clara.ops.challenge.document_management_service_challenge.domain.entity.DocumentTag;
import com.clara.ops.challenge.document_management_service_challenge.domain.mapper.DocumentMapper;
import com.clara.ops.challenge.document_management_service_challenge.domain.repository.IDocumentRepository;
import com.clara.ops.challenge.document_management_service_challenge.domain.repository.IDocumentTagRepository;
import com.clara.ops.challenge.document_management_service_challenge.exception.BusinessException;
import com.clara.ops.challenge.document_management_service_challenge.infrastructure.MinioService;
import java.math.BigInteger;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.multipart.MultipartFile;

@ExtendWith(MockitoExtension.class)
public class DocumentServiceTest {

  @Mock private IDocumentRepository repository;

  @Mock private IDocumentTagRepository tagRepository;

  @Mock private MinioService minioService;

  @Mock private DocumentMapper mapper;

  @InjectMocks private DocumentService service;

  @Test
  void uploadDocument_shouldUploadFileAndPersistDocumentAndTags() {
    MultipartFile file = mock(MultipartFile.class);
    when(file.getContentType()).thenReturn("application/pdf");
    UploadDocumentRequest request =
        new UploadDocumentRequest("marcelo", "contract.pdf", Set.of("legal", "2026"));
    Document doc = Document.builder().id(10).filePath("marcelo/contract.pdf").build();
    Set<DocumentTag> tags =
        Set.of(
            DocumentTag.builder().document(doc).tagName("legal").build(),
            DocumentTag.builder().document(doc).tagName("2026").build());

    when(mapper.toDocument(request, file)).thenReturn(doc);
    when(mapper.toDocumentTags(request, doc)).thenReturn(tags);

    service.uploadDocument(file, request);

    verify(minioService).uploadFile(file, "marcelo/contract.pdf");
    verify(mapper).toDocument(request, file);
    verify(repository).save(doc);
    verify(mapper).toDocumentTags(request, doc);
    verify(tagRepository).saveAll(tags);
    verifyNoMoreInteractions(minioService, repository, tagRepository, mapper);
  }

  @Test
  void uploadDocument_shouldThrowBusinessException_whenFileIsNotPdf() {
    MultipartFile file = mock(MultipartFile.class);
    when(file.getContentType()).thenReturn("image/png");

    UploadDocumentRequest request =
            new UploadDocumentRequest("marcelo", "contract.png", Set.of("legal"));

    assertThatThrownBy(() -> service.uploadDocument(file, request))
            .isInstanceOf(BusinessException.class)
            .hasMessage("Only PDF files are allowed.");

    verifyNoInteractions(minioService, repository, tagRepository, mapper);
  }

  @Test
  void searchDocuments_shouldUseDefaultSort_whenSortIsNull() {
    DocumentSearchFiltersRequest filters =
        new DocumentSearchFiltersRequest("marcelo", "contract", Set.of("legal"));

    Page<Document> pageResult = mockPageDocumentResult();
    PaginatedDocumentSearchResponse expected = mockExpectedPaginatedDocumentResponse();

    when(repository.searchByFilters(
            eq("marcelo"), eq("contract"), eq(Set.of("legal")), any(Pageable.class)))
        .thenReturn(pageResult);
    when(mapper.toPaginatedDocumentSearchResponse(pageResult)).thenReturn(expected);

    PaginatedDocumentSearchResponse actual = service.searchDocuments(0, 20, null, filters);

    ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
    verify(repository)
        .searchByFilters(
            eq("marcelo"), eq("contract"), eq(Set.of("legal")), pageableCaptor.capture());

    Pageable pageableUsed = pageableCaptor.getValue();
    Sort.Order createdAtOrder = pageableUsed.getSort().getOrderFor("createdAt");

    assertThat(pageableUsed.getPageNumber()).isEqualTo(0);
    assertThat(pageableUsed.getPageSize()).isEqualTo(20);
    assertThat(createdAtOrder).isNotNull();
    assertThat(createdAtOrder.isDescending()).isTrue();
    assertThat(actual).isSameAs(expected);
  }

  @Test
  void searchDocuments_shouldApplyProvidedSortFields_currentImplementationAscOnly() {
    DocumentSearchFiltersRequest filters = new DocumentSearchFiltersRequest(null, null, null);
    Page<Document> pageResult = new PageImpl<>(List.of());
    PaginatedDocumentSearchResponse expected = PaginatedDocumentSearchResponse.builder().build();

    when(repository.searchByFilters(isNull(), isNull(), isNull(), any(Pageable.class)))
        .thenReturn(pageResult);
    when(mapper.toPaginatedDocumentSearchResponse(pageResult)).thenReturn(expected);

    service.searchDocuments(1, 10, List.of("filename", "username"), filters);

    ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
    verify(repository).searchByFilters(isNull(), isNull(), isNull(), pageableCaptor.capture());

    Pageable pageableUsed = pageableCaptor.getValue();
    assertThat(pageableUsed.getPageNumber()).isEqualTo(1);
    assertThat(pageableUsed.getPageSize()).isEqualTo(10);
    assertThat(Objects.requireNonNull(pageableUsed.getSort().getOrderFor("filename")).isAscending())
        .isTrue();
    assertThat(Objects.requireNonNull(pageableUsed.getSort().getOrderFor("username")).isAscending())
        .isTrue();
  }

  // TODO sort test
  @Disabled("Enable after implementing parsing of 'field,direction' in createSort")
  @Test
  void searchDocuments_shouldParseDirection_fromSortParam() {
    DocumentSearchFiltersRequest filters = new DocumentSearchFiltersRequest(null, null, null);
    when(repository.searchByFilters(isNull(), isNull(), isNull(), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of()));
    when(mapper.toPaginatedDocumentSearchResponse(any()))
        .thenReturn(PaginatedDocumentSearchResponse.builder().build());

    service.searchDocuments(0, 20, List.of("createdAt,desc"), filters);

    ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
    verify(repository).searchByFilters(isNull(), isNull(), isNull(), pageableCaptor.capture());

    Sort.Order order = pageableCaptor.getValue().getSort().getOrderFor("createdAt");
    assertThat(order).isNotNull();
    assertThat(order.isDescending()).isTrue();
  }

  @Test
  void getDocumentDownloadUrl_shouldReturnUrl_whenDocumentExists() {
    Document doc = Document.builder().id(99).filePath("marcelo/contract.pdf").build();
    when(repository.findById(99)).thenReturn(Optional.of(doc));
    when(minioService.generatePresignedUrl("marcelo/contract.pdf"))
        .thenReturn("https://signed-url");

    DocumentDownloadUrlResponse response = service.getDocumentDownloadUrl(99);

    assertThat(response).isNotNull();
    assertThat(response.getUrl()).isEqualTo("https://signed-url");
    verify(repository).findById(99);
    verify(minioService).generatePresignedUrl("marcelo/contract.pdf");
  }

  @Test
  void getDocumentDownloadUrl_shouldThrowBusinessException_whenDocumentNotFound() {
    when(repository.findById(123)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.getDocumentDownloadUrl(123))
        .isInstanceOf(BusinessException.class)
        .hasMessage("Document not found");

    verify(repository).findById(123);
    verifyNoInteractions(minioService);
  }

  private PaginatedDocumentSearchResponse mockExpectedPaginatedDocumentResponse() {
    return PaginatedDocumentSearchResponse.builder()
        .metadata(
            MetadataResponse.builder()
                .currentPage(0)
                .itemsPerPage(20)
                .currentItems(2)
                .totalPages(1)
                .totalItems(2)
                .build())
        .document(
            List.of(
                DocumentResponse.builder()
                    .id("1")
                    .user("marcelo")
                    .name("contract-v1.pdf")
                    .tags(List.of("legal", "finance"))
                    .size(BigInteger.valueOf(1024))
                    .type("application/pdf")
                    .createdAt("2026-03-26T10:15:30Z")
                    .build(),
                DocumentResponse.builder()
                    .id("2")
                    .user("marcelo")
                    .name("contract-v2.pdf")
                    .tags(List.of("legal"))
                    .size(BigInteger.valueOf(2048))
                    .type("application/pdf")
                    .createdAt("2026-03-26T11:00:00Z")
                    .build()))
        .build();
  }

  private Page<Document> mockPageDocumentResult() {
    Document doc1 =
        Document.builder()
            .id(1)
            .username("marcelo")
            .filename("contract-v1.pdf")
            .filePath("marcelo/contract-v1.pdf")
            .fileSize(BigInteger.valueOf(1024))
            .fileType("application/pdf")
            .createdAt(ZonedDateTime.parse("2026-03-26T10:15:30Z"))
            .documentTags(
                List.of(
                    DocumentTag.builder().tagName("legal").build(),
                    DocumentTag.builder().tagName("finance").build()))
            .build();

    Document doc2 =
        Document.builder()
            .id(2)
            .username("marcelo")
            .filename("contract-v2.pdf")
            .filePath("marcelo/contract-v2.pdf")
            .fileSize(BigInteger.valueOf(2048))
            .fileType("application/pdf")
            .createdAt(ZonedDateTime.parse("2026-03-26T11:00:00Z"))
            .documentTags(List.of(DocumentTag.builder().tagName("legal").build()))
            .build();

    return new PageImpl<>(List.of(doc1, doc2), PageRequest.of(0, 20), 2);
  }
}
