package io.smithy.beam.core;

/** Tracks which compliance-test helper functions are referenced during emission. */
public final class BeamComplianceHelperNeeds {

  private boolean headersConverter;
  private boolean queryParamsConverter;
  private boolean assertHeaders;
  private boolean assertQueryParams;
  private boolean assertForbidHeaders;
  private boolean assertRequireHeaders;
  private boolean assertForbidQueryParams;
  private boolean assertRequireQueryParams;
  private boolean assertResolvedHost;

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

  public void needAssertForbidHeaders() {
    assertForbidHeaders = true;
  }

  public void needAssertRequireHeaders() {
    assertRequireHeaders = true;
  }

  public void needAssertForbidQueryParams() {
    assertForbidQueryParams = true;
  }

  public void needAssertRequireQueryParams() {
    assertRequireQueryParams = true;
  }

  public void needAssertResolvedHost() {
    assertResolvedHost = true;
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

  public boolean assertForbidHeaders() {
    return assertForbidHeaders;
  }

  public boolean assertRequireHeaders() {
    return assertRequireHeaders;
  }

  public boolean assertForbidQueryParams() {
    return assertForbidQueryParams;
  }

  public boolean assertRequireQueryParams() {
    return assertRequireQueryParams;
  }

  public boolean assertResolvedHost() {
    return assertResolvedHost;
  }

  public boolean any() {
    return headersConverter
        || queryParamsConverter
        || assertHeaders
        || assertQueryParams
        || assertForbidHeaders
        || assertRequireHeaders
        || assertForbidQueryParams
        || assertRequireQueryParams
        || assertResolvedHost;
  }
}
