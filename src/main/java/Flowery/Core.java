package Flowery;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;
import com.sedmelluq.discord.lavaplayer.track.playback.NonAllocatingAudioFrameBuffer;
import discord4j.common.util.Snowflake;
import discord4j.core.DiscordClientBuilder;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.lifecycle.ReadyEvent;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.VoiceState;
import discord4j.core.object.entity.Member;
import discord4j.core.object.entity.User;
import discord4j.core.object.entity.channel.MessageChannel;
import discord4j.core.object.entity.channel.VoiceChannel;
import discord4j.voice.AudioProvider;
import discord4j.voice.VoiceConnection;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import Flowery.Interfaces.Command;

public class Core {

	public static void main(String[] args) {

		// Creates AudioPlayer instances and translates URLs to AudioTrack instances
		final AudioPlayerManager playerManager = new DefaultAudioPlayerManager();

		// This is an optimization strategy that Discord4J can utilize.
		// It is not important to understand
		playerManager.getConfiguration()
		    .setFrameBufferFactory(NonAllocatingAudioFrameBuffer::new);

		// Allow playerManager to parse remote sources like YouTube links
		AudioSourceManagers.registerRemoteSources(playerManager);

		// Create an AudioPlayer so Discord4J can receive audio data
		final AudioPlayer player = playerManager.createPlayer();

		// We will be creating LavaPlayerAudioProvider in the next step
		AudioProvider provider = new LavaPlayerAudioProvider(player);
		
		final GatewayDiscordClient client = DiscordClientBuilder.create(args[0]).build().login().block();
		client.getEventDispatcher().on(MessageCreateEvent.class)
	    .flatMap(event -> Mono.just(event.getMessage().getContent())
	        .flatMap(content -> Flux.fromIterable(commands.entrySet())
	            // We will be using ¬ as our "prefix" to any command in the system.
	            .filter(entry -> content.startsWith('¬' + entry.getKey()))
	            .flatMap(entry -> entry.getValue().execute(event))
	            .next()))
	    .subscribe();
		
		// Commands that require variables, input, etc
		
		commands.put("join", event -> Mono.justOrEmpty(event.getMember())
			    .flatMap(Member::getVoiceState)
			    .flatMap(VoiceState::getChannel)
			    // join returns a VoiceConnection which would be required if we were
			    // adding disconnection features, but for now we are just ignoring it.
			    .flatMap(channel -> channel.join(spec -> spec.setProvider(provider)))
			    .then());
		
		final TrackScheduler scheduler = new TrackScheduler(player);
		commands.put("play", event -> Mono.justOrEmpty(event.getMessage().getContent())
		    .map(content -> Arrays.asList(content.split(" ")))
		    .doOnNext(command -> playerManager.loadItem(command.get(1), scheduler))
		    .then());
		
		
		client.onDisconnect().block();
		

	}

	private static final Map<String, Command> commands = new HashMap<>();
	
	
	// static commands 
	static {
		commands.put("ping",
				event -> event.getMessage().getChannel().flatMap(channel -> channel.createMessage("<:pinged:747783377322508290>")).then());

	}
	
}