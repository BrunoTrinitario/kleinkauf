package com.kleinkauf.observability.collector;

import com.kleinkauf.observability.dto.PlatformOverviewDto;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicReference;

/*
The collector's last reading, in memory. With more than one observability instance each one has its own
(they all read the same thing, so they match except for a few seconds).
 */
@Component
public class PlatformState {

    private final AtomicReference<PlatformOverviewDto> overview = new AtomicReference<>(PlatformOverviewDto.empty());

    public PlatformOverviewDto overview() {
        return overview.get();
    }

    void update(PlatformOverviewDto value) {
        overview.set(value);
    }
}
