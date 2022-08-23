package org.matsim.alonso_mora.scheduling;

import org.matsim.api.core.v01.network.Link;

public interface AlonsoMoraTaskFactory {

    WaitForStopTask createWaitForStopTask(double beginTime, double endTime, Link link);

}
