package com.senne.imttech.weapons;

import net.minecraft.server.level.ServerLevel;

/**
 * Een wezen dat een EMP-granaat uitschakelt (hoofdstuk 35): een robot, een security drone, een drone van de toren, een
 * raiddrone of een drone uit je eigen arsenaal. {@link Emp} zet de tijd al in het wezen (Emp.isJammed kijkt daarnaar);
 * hier doet het wezen zelf wat er meteen moet gebeuren (een robot valt neer, een vervoerdrone laat los).
 */
public interface EmpTarget {
    /** Net geraakt door een EMP-puls die {@code ticks} lang duurt. */
    default void onEmp(ServerLevel level, int ticks) {}
}
