package wintahh.rageutils.module;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

public abstract class Module {
    private boolean enabled = false;
    private final String name;
    private final String command;

    public Module(String name, String command) {
        this.name = name;
        this.command = command;
    }

    public void toggle() {
        enabled = !enabled;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) {
            mc.player.sendMessage(
                Text.literal("[RageUtils] " + name + ": " + (enabled ? "§aON" : "§cOFF")),
                true
            );
        }
    }

    public boolean isEnabled() { return enabled; }
    public String getName() { return name; }
    public String getCommand() { return command; }
}