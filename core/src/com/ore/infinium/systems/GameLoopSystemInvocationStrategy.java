/**
 * ***************************************************************************
 * Copyright (C) 2015 by Shaun Reich <sreich02@gmail.com>                   *
 * Adopted from previously MIT-licensed code, by:
 *
 * @author piotr-j
 * @author Daan van Yperen
 * *
 * This program is free software; you can redistribute it and/or            *
 * modify it under the terms of the GNU General Public License as           *
 * published by the Free Software Foundation; either version 2 of           *
 * the License, or (at your option) any later version.                      *
 * *
 * This program is distributed in the hope that it will be useful,          *
 * but WITHOUT ANY WARRANTY; without even the implied warranty of           *
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the            *
 * GNU General Public License for more details.                             *
 * *
 * You should have received a copy of the GNU General Public License        *
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.    *
 * ***************************************************************************
 */

package com.ore.infinium.systems;

import com.artemis.BaseSystem;
import com.artemis.SystemInvocationStrategy;
import com.artemis.utils.Bag;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.TimeUtils;
import com.ore.infinium.systems.profiler.SystemProfiler;

public class GameLoopSystemInvocationStrategy extends SystemInvocationStrategy {

    //systems marked as indicating to be run only during the logic section of the loop
    private final Array<SystemAndProfiler> m_renderSystems = new Array<>();
    private final Array<SystemAndProfiler> m_logicSystems = new Array<>();

    private class SystemAndProfiler {
        BaseSystem system;
        SystemProfiler profiler;

        public SystemAndProfiler(BaseSystem _system, SystemProfiler _systemProfiler) {
            system = _system;
            profiler = _systemProfiler;
        }
    }

    private long m_accumulator;

    //delta time
    private long m_nsPerTick;
    private long m_currentTime = System.nanoTime();

    private boolean m_systemsSorted;

    protected SystemProfiler frameProfiler;
    private boolean initialized = false;
    private boolean m_isServer;

    /**
     * @param msPerTick
     *         desired ms per tick you want the logic systems to run at.
     *         Rendering is unbounded/probably bounded by libgdx's
     *         DesktopLauncher
     */
    public GameLoopSystemInvocationStrategy(int msPerTick, boolean isServer) {
        m_isServer = isServer;
        m_nsPerTick = TimeUtils.millisToNanos(msPerTick);
    }

    private void addSystems(Bag<BaseSystem> systems) {
        if (!m_systemsSorted) {
            Object[] systemsData = systems.getData();
            for (int i = 0; i < systems.size(); ++i) {
                BaseSystem system = (BaseSystem) systemsData[i];
                if (system instanceof RenderSystemMarker) {
                    m_renderSystems.add(new SystemAndProfiler(system, createSystemProfiler(system)));
                } else {
                    m_logicSystems.add(new SystemAndProfiler(system, createSystemProfiler(system)));
                }
            }
        }
    }

    protected void initialize() {
        if (!m_isServer) {
            createFrameProfiler();
        }
    }

    private void createFrameProfiler() {
        frameProfiler = SystemProfiler.create("Frame Profiler");
        frameProfiler.setColor(1, 1, 1, 1);
    }

    private void processProfileSystem(SystemProfiler profiler, BaseSystem system) {
        if (profiler != null) {
            profiler.start();
        }

        system.process();

        if (profiler != null) {
            profiler.stop();
        }
    }

    private SystemProfiler createSystemProfiler(BaseSystem system) {
        SystemProfiler old = null;

        if (!m_isServer) {
            old = SystemProfiler.getFor(system);
            if (old == null) {
                old = SystemProfiler.createFor(system, world);
            }
        }

        return old;
    }

    @Override
    protected void process(Bag<BaseSystem> systems) {

        if (!m_isServer) {
            frameProfiler.start();
        }

        //fixme isn't this(initialized) called automatically??
        if (!initialized) {
            initialize();
            initialized = true;
        }

        if (!m_systemsSorted) {
            addSystems(systems);
            m_systemsSorted = true;
        }

        long newTime = System.nanoTime();
        //nanoseconds
        long frameTime = newTime - m_currentTime;

        //ms per frame
        final long minMsPerFrame = 250;
        if (frameTime > TimeUtils.millisToNanos(minMsPerFrame)) {
            frameTime = TimeUtils.millisToNanos(minMsPerFrame);    // Note: Avoid spiral of death
        }

        m_currentTime = newTime;
        m_accumulator += frameTime;

        //convert from nanos to millis then to seconds, to get fractional second dt
        world.setDelta(TimeUtils.nanosToMillis(m_nsPerTick) / 1000.0f);

        while (m_accumulator >= m_nsPerTick) {
            /** Process all entity systems inheriting from {@link RenderSystemMarker} */
            for (int i = 0; i < m_logicSystems.size; i++) {
                SystemAndProfiler systemAndProfiler = m_logicSystems.get(i);
                //TODO interpolate before this
                processProfileSystem(systemAndProfiler.profiler, systemAndProfiler.system);
                updateEntityStates();
            }

            m_accumulator -= m_nsPerTick;
        }

        //Gdx.app.log("frametime", Double.toString(frameTime));
        //Gdx.app.log("alpha", Double.toString(alpha));
        //try {
        //    int sleep = (int)Math.max(newTime + CLIENT_FIXED_TIMESTEP - TimeUtils.millis()/1000.0, 0.0);
        //    Gdx.app.log("", "sleep amnt: " + sleep);
        //    Thread.sleep(sleep);
        //} catch (InterruptedException e) {
        //    e.printStackTrace();
        //}

        //float alpha = (float) m_accumulator / m_nsPerTick;

        //only clear if we have something to render..aka this world is a rendering one (client)
        //else it's a server, and this will crash due to no gl context, obviously
        if (m_renderSystems.size > 0) {
            Gdx.gl.glClearColor(.1f, .1f, .1f, 1);
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        }

        for (int i = 0; i < m_renderSystems.size; i++) {
            //TODO interpolate this rendering with the state from the logic run, above
            //State state = currentState * alpha +
            //previousState * ( 1.0 - alpha );

            SystemAndProfiler systemAndProfiler = m_renderSystems.get(i);
            //TODO interpolate before this
            processProfileSystem(systemAndProfiler.profiler, systemAndProfiler.system);

            updateEntityStates();
        }

        if (!m_isServer) {
            frameProfiler.stop();
        }
    }
}
