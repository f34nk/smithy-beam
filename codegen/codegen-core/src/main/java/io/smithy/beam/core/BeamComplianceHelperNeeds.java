package io.smithy.beam.core;

/** Tracks which compliance-test helper functions are referenced during emission. */
public final class BeamComplianceHelperNeeds {

  private boolean headersConverter;
  private boolean queryParamsConverter;
  private boolean assertHeaders;
  private boolean assertQueryParams;

  public void needHeadersConverter() {
    headersConverter = true;
  }

  public void needQueryParamsConverter() {
    queryParamsConverter = true;
  }

  public void needAssertHeaders() {
    assertHeaders = true;
  }

  public void needAssertQueryParams() {
    assertQueryParams = true;
  }

  public boolean headersConverter() {
    return headersConverter;
  }

  public boolean queryParamsConverter() {
    return queryParamsConverter;
  }

  public boolean assertHeaders() {
    return assertHeaders;
  }

  public boolean assertQueryParams() {
    return assertQueryParams;
  }

  public boolean any() {
    return headersConverter || queryParamsConverter || assertHeaders || assertQueryParams;
  }
}
