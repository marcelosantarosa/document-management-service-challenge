package com.clara.ops.challenge.document_management_service_challenge.domain.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.clara.ops.challenge.document_management_service_challenge.controller.request.UploadDocumentRequest;
import com.clara.ops.challenge.document_management_service_challenge.controller.response.DocumentResponse;
import com.clara.ops.challenge.document_management_service_challenge.controller.response.MetadataResponse;
import com.clara.ops.challenge.document_management_service_challenge.controller.response.PaginatedDocumentSearchResponse;
import com.clara.ops.challenge.document_management_service_challenge.domain.entity.Document;
import com.clara.ops.challenge.document_management_service_challenge.domain.entity.DocumentTag;
import java.math.BigInteger;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.mock.web.MockMultipartFile;

public class DocumentMapperTest {

  private final DocumentMapper mapper = new DocumentMapper();

  @Test
  void toDocument_shouldMapUploadRequestAndFile() {
    UploadDocumentRequest request =
        new UploadDocumentRequest("marcelo", "contract.pdf", Set.of("legal", "finance"));
    byte[] content = "fake-pdf-content".getBytes();
    MockMultipartFile file =
        new MockMultipartFile("file", "contract.pdf", "application/pdf", content);

    ZonedDateTime before = ZonedDateTime.now();
    Document result = mapper.toDocument(request, file);
    ZonedDateTime after = ZonedDateTime.now();

    assertThat(result.getId()).isNull();
    assertThat(result.getUsername()).isEqualTo("marcelo");
    assertThat(result.getFilename()).isEqualTo("contract.pdf");
    assertThat(result.getFilePath()).isEqualTo("marcelo/contract.pdf");
    assertThat(result.getFileSize()).isEqualTo(BigInteger.valueOf(content.length));
    assertThat(result.getFileType()).isEqualTo("application/pdf");
    assertThat(result.getCreatedAt()).isBetween(before, after);
  }

  @Test
  void toDocumentTags_shouldMapAllTagsWithDocumentReference() {
    UploadDocumentRequest request =
        new UploadDocumentRequest("marcelo", "contract.pdf", Set.of("legal", "finance"));
    Document doc = Document.builder().id(10).username("marcelo").filename("contract.pdf").build();

    Set<DocumentTag> result = mapper.toDocumentTags(request, doc);

    assertThat(result).hasSize(2);
    assertThat(result)
        .extracting(DocumentTag::getTagName)
        .containsExactlyInAnyOrder("legal", "finance");
    assertThat(result).allSatisfy(tag -> assertThat(tag.getDocument()).isSameAs(doc));
  }

  @Test
  void toMetadataResponse_shouldMapPageMetadata() {
    Page<Document> documents =
        new PageImpl<>(
            List.of(Document.builder().id(1).build(), Document.builder().id(2).build()),
            PageRequest.of(1, 2),
            5);

    MetadataResponse result = mapper.toMetadataResponse(documents);

    assertThat(result.getCurrentPage()).isEqualTo(1);
    assertThat(result.getItemsPerPage()).isEqualTo(2);
    assertThat(result.getCurrentItems()).isEqualTo(2);
    assertThat(result.getTotalPages()).isEqualTo(3);
    assertThat(result.getTotalItems()).isEqualTo(5);
  }

  @Test
  void toDocumentResponse_shouldMapDocumentFieldsAndTags() {
    ZonedDateTime createdAt = ZonedDateTime.parse("2026-03-26T12:00:00Z");

    Document doc =
        Document.builder()
            .id(99)
            .username("marcelo")
            .filename("invoice.pdf")
            .fileSize(BigInteger.valueOf(2048))
            .fileType("application/pdf")
            .createdAt(createdAt)
            .build();

    List<DocumentTag> tags =
        List.of(
            DocumentTag.builder().document(doc).tagName("finance").build(),
            DocumentTag.builder().document(doc).tagName("invoice").build());
    doc.setDocumentTags(tags);

    DocumentResponse result = mapper.toDocumentResponse(doc);

    assertThat(result.getId()).isEqualTo("99");
    assertThat(result.getUser()).isEqualTo("marcelo");
    assertThat(result.getName()).isEqualTo("invoice.pdf");
    assertThat(result.getSize()).isEqualTo(BigInteger.valueOf(2048));
    assertThat(result.getType()).isEqualTo("application/pdf");
    assertThat(result.getTags()).containsExactly("finance", "invoice");
    assertThat(result.getCreatedAt()).isEqualTo("2026-03-26T12:00Z");
  }

  @Test
  void toPaginatedDocumentSearchResponse_shouldMapMetadataAndDocuments() {
    ZonedDateTime createdAt1 = ZonedDateTime.parse("2026-03-26T10:15:30Z");
    ZonedDateTime createdAt2 = ZonedDateTime.parse("2026-03-26T11:00:00Z");

    Document doc1 =
        Document.builder()
            .id(1)
            .username("marcelo")
            .filename("contract-v1.pdf")
            .fileSize(BigInteger.valueOf(1024))
            .fileType("application/pdf")
            .createdAt(createdAt1)
            .build();
    doc1.setDocumentTags(
        List.of(
            DocumentTag.builder().document(doc1).tagName("legal").build(),
            DocumentTag.builder().document(doc1).tagName("finance").build()));

    Document doc2 =
        Document.builder()
            .id(2)
            .username("ana")
            .filename("contract-v2.pdf")
            .fileSize(BigInteger.valueOf(4096))
            .fileType("application/pdf")
            .createdAt(createdAt2)
            .build();
    doc2.setDocumentTags(List.of(DocumentTag.builder().document(doc2).tagName("legal").build()));

    Page<Document> documents = new PageImpl<>(List.of(doc1, doc2), PageRequest.of(0, 20), 2);

    PaginatedDocumentSearchResponse result = mapper.toPaginatedDocumentSearchResponse(documents);

    assertThat(result.getMetadata()).isNotNull();
    assertThat(result.getMetadata().getCurrentPage()).isEqualTo(0);
    assertThat(result.getMetadata().getItemsPerPage()).isEqualTo(20);
    assertThat(result.getMetadata().getCurrentItems()).isEqualTo(2);
    assertThat(result.getMetadata().getTotalPages()).isEqualTo(1);
    assertThat(result.getMetadata().getTotalItems()).isEqualTo(2);

    assertThat(result.getDocument()).hasSize(2);

    DocumentResponse response1 = result.getDocument().get(0);
    assertThat(response1.getId()).isEqualTo("1");
    assertThat(response1.getUser()).isEqualTo("marcelo");
    assertThat(response1.getName()).isEqualTo("contract-v1.pdf");
    assertThat(response1.getTags()).containsExactly("legal", "finance");
    assertThat(response1.getSize()).isEqualTo(BigInteger.valueOf(1024));
    assertThat(response1.getType()).isEqualTo("application/pdf");
    assertThat(response1.getCreatedAt()).isEqualTo("2026-03-26T10:15:30Z");

    DocumentResponse response2 = result.getDocument().get(1);
    assertThat(response2.getId()).isEqualTo("2");
    assertThat(response2.getUser()).isEqualTo("ana");
    assertThat(response2.getName()).isEqualTo("contract-v2.pdf");
    assertThat(response2.getTags()).containsExactly("legal");
    assertThat(response2.getSize()).isEqualTo(BigInteger.valueOf(4096));
    assertThat(response2.getType()).isEqualTo("application/pdf");
    assertThat(response2.getCreatedAt()).isEqualTo("2026-03-26T11:00Z");
  }
}
