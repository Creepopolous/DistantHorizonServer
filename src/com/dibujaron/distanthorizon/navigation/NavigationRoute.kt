package com.dibujaron.distanthorizon.navigation

import com.dibujaron.distanthorizon.DHServer
import com.dibujaron.distanthorizon.docking.ShipDockingPort
import com.dibujaron.distanthorizon.docking.StationDockingPort
import com.dibujaron.distanthorizon.ship.IndexedState
import com.dibujaron.distanthorizon.ship.Ship
import com.dibujaron.distanthorizon.ship.ShipState
import org.json.JSONObject
import java.util.*
const val RETRAIN_THRESHOLD = 1 * DHServer.ticksPerSecond
class NavigationRoute(var ship: Ship, var shipPort: ShipDockingPort, var destination: StationDockingPort) {
    var currentPhase: NavigationPhase = retrain()
    var ticksSinceRetrain = 0
    private fun retrain(): NavigationPhase{
        return trainPhase(0.0) { endTimeEst ->
            val endVel = destination.velocityAtTime(endTimeEst)
            val endPortGlobalPos = destination.globalPosAtTime(endTimeEst)
            val myPortRelative = shipPort.relativePosition()
            val endRotation = destination.globalRotationAtTime(endTimeEst) + shipPort.relativeRotation()
            val targetPos = endPortGlobalPos + (myPortRelative * -1.0).rotated(endRotation)
            val endState = ShipState(targetPos, endRotation, endVel)
            BezierPhase(0.0, ship, ship.currentState, endState)
        }
    }

    fun getEndState(): ShipState{
        return currentPhase.getEndState()
    }

    fun hasNext(delta: Double): Boolean
    {
        return currentPhase.hasNextStep(delta)
    }

    fun next(delta: Double): ShipState
    {
        ticksSinceRetrain++
        if(ticksSinceRetrain > RETRAIN_THRESHOLD)
        {
            retrain()
            ticksSinceRetrain = 0
        }
        return currentPhase.step(delta)
    }

    companion object {

        protected fun trainPhase(
            startTime: Double,
            endTimeToPhaseFunc: (endTime: Double) -> NavigationPhase
        ): NavigationPhase {
            var iterations = 0
            val previousEstimations = LinkedList<Double>()
            previousEstimations.addLast(startTime)
            while (iterations < 100000) {
                if (iterations > 6) {
                    previousEstimations.pollFirst()
                }
                val previousEstimate = previousEstimations.last
                val currentPhase = endTimeToPhaseFunc(previousEstimate)
                val newEstimate = currentPhase.endTime(DHServer.tickLengthSeconds)
                if (newEstimate.isNaN()) {
                    throw IllegalStateException("Phase produced NaN time estimate.")
                }
                val movingAverage = previousEstimations.asSequence().sum() / previousEstimations.size
                val diff = newEstimate - movingAverage
                if (diff < 0.05) {
                    return currentPhase
                }
                previousEstimations.addLast(newEstimate)
                iterations++
            }
            throw IllegalStateException("Phase training failed to converge.")
        }
    }
}