package net.sahibnanda.portfolio.middleware;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

class ClientIpResolverTest {

  private final ClientIpResolver resolver = new ClientIpResolver();

  @Test
  void resolveClientIpReturnsTheHeaderValueWhenPresent() {
    HttpServletRequest request = mock(HttpServletRequest.class);
    when(request.getHeader(ClientIpResolver.X_FORWARDED_FOR))
        .thenReturn("203.0.113.5");

    assertThat(resolver.resolveClientIp(request)).isEqualTo("203.0.113.5");
  }

  @Test
  void resolveClientIpReturnsTheFirstIpInACommaSeparatedChain() {
    HttpServletRequest request = mock(HttpServletRequest.class);
    when(request.getHeader(ClientIpResolver.X_FORWARDED_FOR))
        .thenReturn("203.0.113.5, 70.41.3.18, 150.172.238.178");

    assertThat(resolver.resolveClientIp(request)).isEqualTo("203.0.113.5");
  }

  @Test
  void resolveClientIpFallsBackToRemoteAddrWhenHeaderIsAbsent() {
    HttpServletRequest request = mock(HttpServletRequest.class);
    when(request.getHeader(ClientIpResolver.X_FORWARDED_FOR)).thenReturn(null);
    when(request.getRemoteAddr()).thenReturn("127.0.0.1");

    assertThat(resolver.resolveClientIp(request)).isEqualTo("127.0.0.1");
  }

  @Test
  void resolveClientIpFallsBackToRemoteAddrWhenHeaderIsBlank() {
    HttpServletRequest request = mock(HttpServletRequest.class);
    when(request.getHeader(ClientIpResolver.X_FORWARDED_FOR)).thenReturn("  ");
    when(request.getRemoteAddr()).thenReturn("127.0.0.1");

    assertThat(resolver.resolveClientIp(request)).isEqualTo("127.0.0.1");
  }
}
