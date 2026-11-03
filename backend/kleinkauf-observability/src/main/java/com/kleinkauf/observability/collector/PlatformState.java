package com.kleinkauf.observability.collector;

import com.kleinkauf.observability.dto.PlatformOverviewDto;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicReference;

/*
Ultima lectura del recolector, en memoria. Con mas de una instancia de observability cada una tiene la suya
(todas leen lo mismo, asi que dan lo mismo salvo por unos segundos).
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
