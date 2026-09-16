package com.example.backendbook.lab;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class ConnectionResult {
    private final int value;
    private final double connectionWaitMs;
    private final double totalMs;
}
