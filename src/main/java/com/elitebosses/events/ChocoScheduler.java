package com.elitebosses.events;

import com.elitebosses.EliteBosses;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Ejecuta tareas pequenas unos ticks despues, en el hilo del servidor. */
@Mod.EventBusSubscriber(modid = EliteBosses.MODID)
public final class ChocoScheduler {
    private record Task(long when, Runnable run) {
    }

    private static final List<Task> TASKS = new ArrayList<>();
    private static long now = 0L;

    private ChocoScheduler() {
    }

    public static void runLater(int delayTicks, Runnable run) {
        TASKS.add(new Task(now + Math.max(0, delayTicks), run));
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        now++;
        if (TASKS.isEmpty()) {
            return;
        }
        List<Task> due = new ArrayList<>();
        for (Iterator<Task> it = TASKS.iterator(); it.hasNext(); ) {
            Task task = it.next();
            if (task.when() <= now) {
                due.add(task);
                it.remove();
            }
        }
        for (Task task : due) {
            try {
                task.run().run();
            } catch (Exception ex) {
                EliteBosses.LOGGER.error("Error en una tarea programada de Odio el Chocolate", ex);
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        TASKS.clear();
    }
}
