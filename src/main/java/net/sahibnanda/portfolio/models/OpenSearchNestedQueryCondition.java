package net.sahibnanda.portfolio.models;

import java.util.List;
import lombok.Builder;
import lombok.Singular;
import lombok.Value;

/**
 * A query condition scoped to a {@code nested}-mapped field. The inner
 * conditions are combined the same way a top-level {@code must} list is
 * combined.
 */
@Value
@Builder
public class OpenSearchNestedQueryCondition
    implements OpenSearchQueryCondition {

  /** The path of the nested field to search within. */
  private String path;

  /** The conditions every matching nested document must satisfy. */
  @Singular
  private List<OpenSearchQueryCondition> mustConditions;
}
