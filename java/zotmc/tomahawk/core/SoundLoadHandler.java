package zotmc.tomahawk.core;

import static com.google.common.base.Preconditions.checkState;
import net.minecraft.client.audio.SoundManager;
import net.minecraftforge.client.event.sound.SoundLoadEvent;
import zotmc.tomahawk.data.ModData.AxeTomahawk;
import zotmc.tomahawk.data.ReflData;
import zotmc.tomahawk.util.Utils;

import com.google.common.base.Throwables;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

public class SoundLoadHandler {
	
	SoundLoadHandler() {
		checkState(Utils.MC_VERSION.isBelow("1.7.2"));
	}
	
	private void addSound(SoundManager manager, String path) {
		try {
			ReflData.soundManagers.get().addSound.invoke(manager, path);
		} catch (Throwable e) {
			throw Throwables.propagate(e);
		}
	}
	
	@SubscribeEvent public void onSoundLoad(SoundLoadEvent event) {
		@SuppressWarnings("deprecation")
		SoundManager m = event.manager;
		
		addSound(m, AxeTomahawk.DOMAIN + ":random/tomahawk");
		addSound(m, AxeTomahawk.DOMAIN + ":random/tomahawk_hit1");
		addSound(m, AxeTomahawk.DOMAIN + ":random/tomahawk_hit2");
		addSound(m, AxeTomahawk.DOMAIN + ":random/tomahawk_hit3");
		addSound(m, AxeTomahawk.DOMAIN + ":random/tomahawk_hit4");
	}

}