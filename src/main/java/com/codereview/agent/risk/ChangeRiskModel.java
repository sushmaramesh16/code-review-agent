package com.codereview.agent.risk;

import com.codereview.agent.model.ChangeStats;

/** Estimates bug risk for a whole change from its size and spread. */
public interface ChangeRiskModel {

    ChangeRisk assess(ChangeStats stats);
}
