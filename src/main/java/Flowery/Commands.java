package Flowery;

import discord4j.core.event.domain.message.MessageCreateEvent;
import reactor.core.publisher.Mono;

public class Commands {
	interface Command {
		Mono<Void> execute(MessageCreateEvent event);
	}
}
