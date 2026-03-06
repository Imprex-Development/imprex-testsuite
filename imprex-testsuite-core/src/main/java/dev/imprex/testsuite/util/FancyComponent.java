package dev.imprex.testsuite.util;

import com.mattmalec.pterodactyl4j.UtilizationState;

import net.kyori.adventure.text.Component;

public class FancyComponent {

	public static Component beautifyServerStatus(PteroServerStatus status) {
		return Component
				.text(beautifyEnum(status))
				.color(switch (status) {
				case INSTALLING -> Chat.Color.PURPLE;
				case READY -> Chat.Color.LIGHT_GREEN;
				case TRANSFERING -> Chat.Color.ORANGE;
				case SUSPENDED -> Chat.Color.RED;
				case UNKNOWN -> Chat.Color.GRAY;
				});
	}

	public static Component beautifyUtilizationState(UtilizationState state) {
		return Component
				.text(beautifyEnum(state))
				.color(switch (state) {
				case STARTING -> Chat.Color.LIGHT_GREEN;
				case RUNNING -> Chat.Color.DARK_GREEN;
				case STOPPING -> Chat.Color.ORANGE;
				case OFFLINE -> Chat.Color.RED;
				});
	}
	
	public static String beautifyEnum(Enum<?> enumName) {
		return enumName.name().substring(0, 1) + enumName.name().substring(1).toLowerCase();
	}
}
