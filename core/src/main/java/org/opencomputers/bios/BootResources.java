package org.opencomputers.bios;

import java.nio.charset.StandardCharsets;

/**
 * Built-in boot resources: the EEPROM BIOS and a demo {@code /init.lua}.
 *
 * The BIOS mirrors OpenComputers' BIOS.lua flow — pick a filesystem, read
 * init.lua, execute it — written against this emulator's component API.
 */
public final class BootResources {
    private BootResources() {
    }

    /** Minimal OC-style BIOS: find a filesystem, load /init.lua, run it. */
    public static final String EEPROM_BIOS = """
            local function checkFs()
              for addr in component.list("filesystem") do
                local fs = component.proxy(addr)
                if fs.exists("/init.lua") then
                  return fs
                end
              end
              return nil
            end

            local fs = checkFs()
            if not fs then
              error("no bootable medium found")
            end

            local handle = fs.open("/init.lua", "r")
            local data = ""
            while true do
              local chunk = fs.read(handle, math.huge)
              if not chunk then break end
              data = data .. chunk
            end
            fs.close(handle)

            local fn, reason = load(data, "=init")
            if not fn then
              error("init.lua parse error: " .. tostring(reason))
            end
            fn()
            """;

    /**
     * Demo init.lua: a terminal that prints a banner, echoes key_down events,
     * and reacts to component plug/unplug — exercises the whole pipeline.
     */
    public static final String INIT_LUA = """
            -- Wire the GPU to the first screen.
            local gpu, screen
            for addr in component.list("gpu") do gpu = component.proxy(addr) break end
            for addr in component.list("screen") do screen = addr break end
            if gpu and screen then
              gpu.bind(screen)
              local w, h = gpu.getResolution()
              gpu.fill(1, 1, w, h, " ")
              gpu.set(1, 1, "OpenComputersAndroid")
              gpu.set(1, 2, string.rep("=", 20))
              gpu.set(1, 3, "type to echo keys; S to shutdown")
            end

            local y = 5
            local function writeLine(s)
              if not gpu then return end
              local w, h = gpu.getResolution()
              if y > h then
                gpu.copy(1, 2, w, h - 1, 0, -1)
                gpu.fill(1, h, w, 1, " ")
                y = h
              end
              gpu.set(1, y, tostring(s))
              y = y + 1
            end

            while true do
              -- Signals arrive as (name, sourceAddress, ...args).
              local name, src, a, b = computer.pullSignal()
              if name == "key_down" then
                local ch = a and string.char(math.floor(a)) or "?"
                writeLine("key: " .. ch .. " (code " .. tostring(b) .. ")")
                if b == 31 then computer.pushSignal("app_shutdown") end
              elseif name == "component_added" then
                writeLine("+ " .. tostring(a) .. " @ " .. tostring(src))
              elseif name == "component_removed" then
                writeLine("- " .. tostring(a))
              elseif name == "redstone_changed" then
                writeLine("redstone changed on side " .. tostring(a))
              end
            end
            """;

    public static byte[] eepromBios() {
        return EEPROM_BIOS.getBytes(StandardCharsets.ISO_8859_1);
    }

    public static byte[] initLua() {
        return INIT_LUA.getBytes(StandardCharsets.ISO_8859_1);
    }
}
