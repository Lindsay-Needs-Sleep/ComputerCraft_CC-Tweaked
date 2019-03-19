/*
 * This file is part of ComputerCraft - http://www.computercraft.info
 * Copyright Daniel Ratcliffe, 2011-2019. Do not distribute without permission.
 * Send enquiries to dratcliffe@gmail.com
 */

package dan200.computercraft.core.computer;

import dan200.computercraft.core.tracking.Tracking;
import dan200.computercraft.shared.turtle.core.TurtleBrain;
import net.minecraft.tileentity.TileEntity;

import java.util.ArrayDeque;
import java.util.Queue;

import static dan200.computercraft.core.computer.MainThread.MAX_COMPUTER_TIME;

/**
 * Keeps track of tasks that a {@link Computer} should run on the main thread and how long that has been spent executing
 * them.
 *
 * This provides rate-limiting mechanism for tasks enqueued with {@link Computer#queueMainThread(Runnable)}, but also
 * those run elsewhere (such as during the turtle's tick - see {@link TurtleBrain#update()}). In order to handle this,
 * the executor goes through three stages:
 *
 * When {@link State#COOL}, the computer is allocated {@link MainThread#MAX_COMPUTER_TIME}ns to execute any work this
 * tick. At the beginning of the tick, we execute as many {@link MainThread} tasks as possible, until our timeframe or
 * the global time frame has expired.
 *
 * Then, when other objects (such as {@link TileEntity}) are ticked, we update how much time we've used using
 * {@link Computer#afterExecuteMainThread(long)}.
 *
 * Now, if anywhere during this period, we use more than our allocated time slice, the executor is marked as
 * {@link State#HOT}. This means it will no longer be able to execute {@link MainThread} tasks (though will still
 * execute tile entity tasks, in order to prevent the main thread from exhausting work every tick).
 *
 * At the beginning of the next tick, we increment the budget e by {@link MainThread#MAX_COMPUTER_TIME} and any
 * {@link State#HOT} executors are marked as {@link State#COOLING}. They will remain cooling until their budget is
 * fully replenished (is equal to {@link MainThread#MAX_COMPUTER_TIME}). Note, this is different to {@link MainThread},
 * which allows running when it has any budget left. When cooling, <em>no</em> tasks are executed - be they on the tile
 * entity or main thread.
 *
 * This mechanism means that, on average, computers will use at most {@link MainThread#MAX_COMPUTER_TIME}ns per second,
 * but one task source will not prevent others from executing.
 *
 * @see MainThread
 * @see Computer#canExecuteMainThread()
 * @see Computer#queueMainThread(Runnable)
 * @see Computer#afterExecuteMainThread(long)
 */
final class MainThreadExecutor
{
    /**
     * The maximum number of {@link MainThread} tasks allowed on the queue.
     */
    private static final int MAX_TASKS = 5000;

    private final Computer computer;

    /**
     * A lock used for any changes to {@link #tasks}, or {@link #onQueue}. This will be
     * used on the main thread, so locks should be kept as brief as possible.
     */
    private final Object queueLock = new Object();

    /**
     * The queue of tasks which should be executed.
     *
     * @see #queueLock
     */
    private final Queue<Runnable> tasks = new ArrayDeque<>( 4 );

    /**
     * Determines if this executor is currently present on the queue.
     *
     * This should be true iff {@link #tasks} is non-empty.
     *
     * @see #queueLock
     * @see #enqueue(Runnable)
     * @see #afterExecute(long)
     */
    volatile boolean onQueue;

    /**
     * The remaining budgeted time for this tick. This may be negative, in the case that we've gone over budget.
     *
     * @see #tickCooling()
     * @see #consumeTime(long)
     */
    private long budget = 0;

    /**
     * The last tick that {@link #budget} was updated.
     *
     * @see #tickCooling()
     * @see #consumeTime(long)
     */
    private int currentTick = -1;

    /**
     * The current state of this executor.
     *
     * @see #canExecuteExternal()
     */
    private State state = State.COOL;

    MainThreadExecutor( Computer computer )
    {
        this.computer = computer;
    }

    /**
     * Push a task onto this executor's queue, pushing it onto the {@link MainThread} if needed.
     *
     * @param runnable The task to run on the main thread.
     * @return Whether this task was enqueued (namely, was there space).
     */
    boolean enqueue( Runnable runnable )
    {
        synchronized( queueLock )
        {
            if( tasks.size() >= MAX_TASKS || !tasks.offer( runnable ) ) return false;
            if( !onQueue && state == State.COOL ) MainThread.queue( this );
            return true;
        }
    }

    void execute()
    {
        if( state != State.COOL ) return;

        Runnable task;
        synchronized( queueLock )
        {
            task = tasks.poll();
        }

        if( task != null ) task.run();
    }

    /**
     * Update the time taken to run an {@link #enqueue(Runnable)} task.
     *
     * @param time The time some task took to run.
     * @return Whether this should be added back to the queue.
     */
    boolean afterExecute( long time )
    {
        consumeTime( time );

        synchronized( queueLock )
        {
            if( state != State.COOL || tasks.isEmpty() ) return onQueue = false;
            return true;
        }
    }

    /**
     * Update the time taken to run an external task (one not part of {@link #tasks}), incrementing the appropriate
     * statistics.
     *
     * @param time The time some task took to run
     */
    void afterExecuteExternal( long time )
    {
        consumeTime( time );
        MainThread.consumeTime( time );
    }

    /**
     * Whether we should execute "external" tasks (ones not part of {@link #tasks}).
     *
     * @return Whether we can execute external tasks.
     */
    boolean canExecuteExternal()
    {
        return state != State.COOLING;
    }

    private void consumeTime( long time )
    {
        Tracking.addServerTiming( computer, time );

        // Reset the budget if moving onto a new tick. We know this is safe, as this will only have happened if
        // #tickCooling() isn't called, and so we didn't overrun the previous tick.
        if( currentTick != MainThread.currentTick() )
        {
            currentTick = MainThread.currentTick();
            budget = MAX_COMPUTER_TIME;
        }

        budget -= time;

        // If we've gone over our limit, mark us as having to cool down.
        if( budget < 0 && state == State.COOL )
        {
            state = State.HOT;
            MainThread.cooling( this );
        }
    }

    /**
     * Move this executor forward one tick, replenishing the budget by {@link MainThread#MAX_COMPUTER_TIME}.
     *
     * @return Whether this executor has cooled down, and so is safe to run again.
     */
    boolean tickCooling()
    {
        state = State.COOLING;
        currentTick = MainThread.currentTick();
        budget += Math.min( budget + MAX_COMPUTER_TIME, MAX_COMPUTER_TIME );
        if( budget < MAX_COMPUTER_TIME ) return false;

        state = State.COOL;
        synchronized( queueLock )
        {
            if( !tasks.isEmpty() && !onQueue ) MainThread.queue( this );
        }
        return true;
    }

    private enum State
    {
        COOL,
        HOT,
        COOLING,
    }
}
