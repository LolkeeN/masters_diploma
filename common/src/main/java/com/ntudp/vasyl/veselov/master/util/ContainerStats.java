package com.ntudp.vasyl.veselov.master.util;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
public class ContainerStats {

    private final double cpuPercentage;          // 100.85
    private final long memoryUsageBytes;        // 1.15GB в байтах
    private final long memoryLimitBytes;        // 15.44GB в байтах
    private final long diskReadBytes;           // 0
    private final long diskWriteBytes;          // 0
    private final long networkReceivedBytes;    // 256MB в байтах
    private final long networkSentBytes;        // 129MB в байтах
    private final long timestamp;               // System.currentTimeMillis()

}