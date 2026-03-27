package com.clara.ops.challenge.document_management_service_challenge.controller.request;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "The document search filters.")
public class DocumentSearchFiltersRequest {

  @Schema(description = "The user who uploaded the document.")
  private String user;

  @Schema(description = "The document name.")
  private String name;

  @Schema(description = "The document tags.")
  private Set<String> tags;
}
