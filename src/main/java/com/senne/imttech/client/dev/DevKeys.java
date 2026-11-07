package com.senne.imttech.client.dev;

import com.senne.imttech.ImtTech;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWNativeWin32;
import org.lwjgl.system.windows.User32;

/**
 * Echte toetsaanslagen voor de rondleiding (alleen op Windows). Een toetsbericht (WM_KEYDOWN of WM_KEYUP) gaat in de
 * berichtenrij van het spelvenster, precies zoals Windows dat voor een echt toetsenbord doet. GLFW verwerkt het bij
 * de volgende glfwPollEvents: zijn eigen toetsstand (glfwGetKey) verandert mee en Minecraft krijgt de gewone
 * toetsgebeurtenis. Een nagedane {@code KeyMapping.setDown} doet dat allebei niet; voor het wiel (dat GLFW zelf
 * raadpleegt, zie GearWheelScreen) is dit de enige manier om het stuk vanaf het toetsenbord echt te testen.
 */
public final class DevKeys {
    private DevKeys() {}

    /** De GLFW-toets indrukken ({@code true}) of loslaten ({@code false}). Geeft false als dat hier niet kan. */
    public static boolean press(int glfwKey, boolean down) {
        if (Util.getPlatform() != Util.OS.WINDOWS) {
            ImtTech.LOGGER.warn("[dev] echte toetsen kan alleen op Windows (toets {})", glfwKey);
            return false;
        }
        long hwnd = GLFWNativeWin32.glfwGetWin32Window(Minecraft.getInstance().getWindow().getWindow());
        int scancode = GLFW.glfwGetKeyScancode(glfwKey);
        // lParam: bit 0-15 het aantal herhalingen (1), bit 16-23 de scancode, bit 30 "was al ingedrukt", bit 31 "loslaten".
        long lParam = ((long) (scancode & 0xFF) << 16) | 1L;
        if (!down) {
            lParam |= (1L << 30) | (1L << 31);
        }
        boolean ok = User32.PostMessage(hwnd, down ? User32.WM_KEYDOWN : User32.WM_KEYUP, virtualKey(glfwKey), lParam);
        if (!ok) {
            ImtTech.LOGGER.warn("[dev] toetsbericht voor toets {} ({}) kwam niet aan", glfwKey, down ? "in" : "los");
        }
        return ok;
    }

    /** De Windows-toetscode (virtual key) bij een GLFW-toets; letters, cijfers en de spatie hebben dezelfde code. */
    private static int virtualKey(int glfwKey) {
        return switch (glfwKey) {
            case GLFW.GLFW_KEY_LEFT_ALT, GLFW.GLFW_KEY_RIGHT_ALT -> 0x12;         // VK_MENU
            case GLFW.GLFW_KEY_LEFT_SHIFT, GLFW.GLFW_KEY_RIGHT_SHIFT -> 0x10;     // VK_SHIFT
            case GLFW.GLFW_KEY_LEFT_CONTROL, GLFW.GLFW_KEY_RIGHT_CONTROL -> 0x11; // VK_CONTROL
            case GLFW.GLFW_KEY_ESCAPE -> 0x1B;
            default -> glfwKey;
        };
    }
}
