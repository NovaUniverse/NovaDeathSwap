package net.novauniverse.games.deathswap.game.swapprovider;

public interface SwapProvider {
	public void onGameStart();
	
	public void onGameEnd();
	
	public SwapResult swap();
}