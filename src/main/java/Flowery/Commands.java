package Flowery;

import discord4j.core.event.domain.message.MessageCreateEvent;

public class Commands {
	interface Command {
	    void execute(MessageCreateEvent event);
	}
}
