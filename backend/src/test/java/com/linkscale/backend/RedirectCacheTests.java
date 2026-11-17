package com.linkscale.backend;

import com.linkscale.backend.config.LinkScaleProperties;
import com.linkscale.backend.controller.RedirectController;
import com.linkscale.backend.domain.LinkRecord;
import com.linkscale.backend.dto.UpdateLinkRequest;
import com.linkscale.backend.exception.LinkExpiredException;
import com.linkscale.backend.exception.LinkNotFoundException;
import com.linkscale.backend.repository.LinkRepository;
import com.linkscale.backend.service.ClickTrackingService;
import com.linkscale.backend.service.LinkService;
import com.linkscale.backend.service.RedirectCache;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedirectCacheTests {

    private static final String CODE = "test-link";
    private static final String DESTINATION = "https://example.com/";
    private final Map<String, String> entries = new HashMap<>();
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> values = mock(ValueOperations.class);
    private final LinkRepository links = mock(LinkRepository.class);
    private final LinkService service = new LinkService(
            links, null, null, null, new LinkScaleProperties("http://localhost:8080", null, null),
            new RedirectCache(redis, true));

    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(anyString())).thenAnswer(call -> entries.get(call.getArgument(0)));
        doAnswer(call -> {
            entries.put(call.getArgument(0), call.getArgument(1));
            return null;
        }).when(values).set(anyString(), anyString(), any(Duration.class));
        when(redis.delete(anyString())).thenAnswer(call -> entries.remove(call.getArgument(0)) != null);
    }

    @Test
    void cacheHitSkipsDatabaseLookupAndStillRecordsEachGet() {
        when(links.findByCode(CODE)).thenReturn(Optional.of(link(true, null)));
        ClickTrackingService tracking = mock(ClickTrackingService.class);
        RedirectController controller = new RedirectController(service, tracking);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getMethod()).thenReturn("GET");

        assertThat(controller.redirect(CODE, request).getHeaders().getLocation()).hasToString(DESTINATION);
        assertThat(controller.redirect("TEST-LINK", request).getStatusCode().value()).isEqualTo(302);

        when(request.getMethod()).thenReturn("HEAD");
        assertThat(controller.redirect(CODE, request).getStatusCode().value()).isEqualTo(302);
        verify(links).findByCode(CODE);
        verify(tracking, times(2)).recordClick(1L);
    }

    @Test
    void deactivationEvictsTheCachedRedirect() {
        LinkRecord inactive = link(false, null);
        when(links.findByCode(CODE)).thenReturn(Optional.of(link(true, null)), Optional.of(inactive));
        when(links.updateLink(1L, 2L, true, null, true, null, true, false)).thenReturn(Optional.of(inactive));
        service.resolveRedirect(CODE);

        service.updateLink(1L, 2L, new UpdateLinkRequest(null, null, false));

        assertThat(entries).isEmpty();
        assertThatThrownBy(() -> service.resolveRedirect(CODE)).isInstanceOf(LinkNotFoundException.class);
    }

    @Test
    void deletionEvictsTheCachedRedirect() {
        when(links.findByCode(CODE)).thenReturn(Optional.of(link(true, null)), Optional.empty());
        when(links.softDelete(1L, 2L)).thenReturn(Optional.of(CODE));
        service.resolveRedirect(CODE);

        service.deleteLink(1L, 2L);

        assertThat(entries).isEmpty();
        assertThatThrownBy(() -> service.resolveRedirect(CODE)).isInstanceOf(LinkNotFoundException.class);
    }

    @Test
    void cacheTtlIsCappedByLinkExpiry() {
        OffsetDateTime expiry = OffsetDateTime.now().plusSeconds(20);
        when(links.findByCode(CODE)).thenReturn(Optional.of(link(true, expiry)));

        service.resolveRedirect(CODE);

        ArgumentCaptor<Duration> ttl = ArgumentCaptor.forClass(Duration.class);
        verify(values).set(anyString(), eq("1\n" + expiry + "\n" + DESTINATION), ttl.capture());
        assertThat(ttl.getValue()).isPositive().isLessThanOrEqualTo(Duration.ofSeconds(20));
    }

    @Test
    void expiredCachedTargetIsRejectedEvenIfRedisReturnsIt() {
        when(links.findByCode(CODE)).thenReturn(Optional.of(link(true, null)));
        service.resolveRedirect(CODE);
        entries.replaceAll((key, value) -> "1\n" + OffsetDateTime.now().minusSeconds(1) + "\n" + DESTINATION);

        assertThatThrownBy(() -> service.resolveRedirect(CODE)).isInstanceOf(LinkExpiredException.class);
        verify(links).findByCode(CODE);
    }

    @Test
    void redisReadAndWriteFailuresFallBackToPostgres() {
        when(values.get(anyString())).thenThrow(new RedisConnectionFailureException("offline"));
        doThrow(new RedisConnectionFailureException("offline"))
                .when(values).set(anyString(), anyString(), any(Duration.class));
        when(links.findByCode(CODE)).thenReturn(Optional.of(link(true, null)));

        assertThat(service.resolveRedirect(CODE).destinationUrl()).isEqualTo(DESTINATION);
        verify(links).findByCode(CODE);
    }

    @Test
    void failedEvictionPreventsStaleRedirectsAfterRedisRecovers() {
        when(links.findByCode(CODE)).thenReturn(Optional.of(link(true, null)), Optional.empty());
        when(links.softDelete(1L, 2L)).thenReturn(Optional.of(CODE));
        service.resolveRedirect(CODE);
        when(redis.delete(anyString())).thenThrow(new RedisConnectionFailureException("offline"));

        service.deleteLink(1L, 2L);

        assertThat(entries).hasSize(1);
        assertThatThrownBy(() -> service.resolveRedirect(CODE)).isInstanceOf(LinkNotFoundException.class);
        verify(values).get(anyString());
    }

    private LinkRecord link(boolean active, OffsetDateTime expiresAt) {
        OffsetDateTime now = OffsetDateTime.now();
        return new LinkRecord(1L, CODE, DESTINATION, 2L, null, expiresAt, active, null, now, now);
    }
}
