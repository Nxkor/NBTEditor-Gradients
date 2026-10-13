#!/bin/bash
# Makes versions/<mc>/src from the root source (Minecraft 1.21.11, NBT Editor 2.0.9). Everything that
# differs between versions is rewritten here: never edit versions/*/src, fix the root or this script.
# Run from the project root: port/port.sh [version...]   (no argument: every version)
set -e
all="1.21.10 26.1.2 26.2 26.3"
# the versions of the first NBT Editor (2.0.3, one jar for Minecraft 1.17 to 1.21.4)
legacy="1.17.1 1.18.2 1.19.4 1.20.1 1.20.6 1.21.1 1.21.4"

# NBT Editor 2.0.3 runs on seven versions of Minecraft by going through its own helpers for what changed between
# them (TextInst, ItemTagReferences, its client commands...): the source goes through them too, and one source
# then compiles against the seven. The test is port/devcheck: these versions have no client gametest API.
#
# Until 1.19.4 Minecraft drew with render(PoseStack...), renderButton and renderBackground(PoseStack): the names
# NBT Editor gave its own methods, which do the same on every version. Its jar says method_25394 for those of
# Minecraft, but here, where everything has the names of Mojang, they would be the same methods. So these three
# versions get the mappings of Mojang with those methods of Minecraft called ...Vanilla (versions/<mc>/mappings.jar).
# The jars that are built are not concerned: they say method_25394 like NBT Editor does.
mappings() {
	v=$1
	[ -f versions/$v/mappings.jar ] && return
	# the file Loom made for officialMojangMappings(): there once this version was set up with them
	from=$(cygpath -u "${GRADLE_USER_HOME:-$HOME/.gradle}")/caches/fabric-loom/$v/layered/loom.mappings-layered+hash.2198.jar
	work=$(mktemp -d)
	unzip -q "$from" -d $work
	sed -i 's/\tmethod_25394\trender\t/\tmethod_25394\trenderVanilla\t/; s/\tmethod_25359\trenderButton\t/\tmethod_25359\trenderButtonVanilla\t/
		s/\t(Lnet\/minecraft\/class_4587;)V\tmethod_25420\trenderBackground\t/\t(Lnet\/minecraft\/class_4587;)V\tmethod_25420\trenderBackgroundVanilla\t/' $work/mappings/mappings.tiny
	"$JAVA_HOME/bin/jar" cf versions/$v/mappings.jar -C $work mappings
	rm -rf $work
}

legacy() {
	v=$1
	d=versions/$v/src
	case $v in 1.17.1|1.18.2|1.19.4) mappings $v;; esac
	M=$d/main/java/com/imnot/nbteditorgradients
	java=21
	case $v in 1.17.1) java=16;; 1.18.2|1.19.4|1.20.1) java=17;; esac
	sed -i "s/\"minecraft\": \"~1.21.11\"/\"minecraft\": \"$v\"/; s/\"java\": \">=21\"/\"java\": \">=$java\"/; s/\"nbteditor\": \">=2.0.9\"/\"nbteditor\": \">=2.0.3 <2.0.9\"/" $d/main/resources/fabric.mod.json
	# Fabric API was only called "fabric" at first (1.17.1 knows no other name), and still answers to it
	sed -i 's/"fabric-api": "\*"/"fabric": "*"/' $d/main/resources/fabric.mod.json
	sed -i "s/JAVA_21/JAVA_$java/" $d/main/resources/nbteditorgradients.mixins.json
	# the colour picker of 2.0.3 draws its square right: nothing to draw over it but the ring
	sed -i '/the ported NBT Editor draws the square/,/^\t\t}$/d' $M/mixin/ColorSelectorWidgetMixin.java
	rm -rf $d/gametest && cp -r port/devcheck $d/gametest

	# texts: Component.literal came with 1.19; TextInst gives an EditableText, with the names NBT Editor gave its methods
	sed -i 's/Component\.literal(/TextInst.literal(/g; s/Component\.translatable(/TextInst.translatable(/g; s/Component\.empty()/TextInst.literal("")/g
		s/\.withStyle(/.formatted(/g; s/\.withColor(selected\.rgb)/.styled(style -> style.withColor(selected.rgb))/
		s/\bMutableComponent\b/EditableText/g; /^import net\.minecraft\.network\.chat\.EditableText;/d' $M/*.java
	for f in $(grep -l 'TextInst\|EditableText' $M/*.java); do
		sed -i '/^package /a import com.luneruniverse.minecraft.mod.nbteditor.multiversion.EditableText;\nimport com.luneruniverse.minecraft.mod.nbteditor.multiversion.TextInst;' $f
	done

	# JSON texts are read with the registries from 1.20.5 only, and a colour name gives a DataResult from 1.20.3 only
	sed -i 's/EditableText json = ComponentSerialization\.CODEC/EditableText json = TextInst.copy(TextInst.fromJson(trimmed));/; /createSerializationContext/d
		s/, HolderLookup\.Provider registries//; s/, registries)/)/
		/^import net\.minecraft\.core\.HolderLookup;/d; /^import net\.minecraft\.network\.chat\.ComponentSerialization;/d' $M/ColorText.java
	sed -i 's/, Minecraft\.getInstance()\.player\.registryAccess()//' $M/DisplayAddon.java
	sed -i 's/, ctx\.getSource()\.getPlayer()\.registryAccess()//' $M/NbtEditorGradients.java
	case $v in 1.17.1|1.18.2|1.19.4|1.20.1)
		sed -i 's/TextColor\.parseColor(\(.*\))\.result()\.orElse(null)/TextColor.parseColor(\1)/' $M/ColorText.java
	;; esac

	# the name and the lore of an item: components from 1.20.5, NBT before; the client commands of Fabric: 1.19
	sed -i 's/net\.fabricmc\.fabric\.api\.client\.command\.v2\./com.luneruniverse.minecraft.mod.nbteditor.multiversion.commands./
		s/item\.set(DataComponents\.CUSTOM_NAME, name);/new LocalItemStack(item).setName(name);/
		s/new ArrayList<>(item\.getOrDefault(DataComponents\.LORE, ItemLore\.EMPTY)\.lines())/ItemTagReferences.LORE.get(item)/
		s/item\.set(DataComponents\.LORE, new ItemLore(lines));/ItemTagReferences.LORE.set(item, lines);/
		s/held\.getOrDefault(DataComponents\.LORE, ItemLore\.EMPTY)\.lines()/ItemTagReferences.LORE.get(held)/
		s/^import net\.minecraft\.core\.component\.DataComponents;/import com.luneruniverse.minecraft.mod.nbteditor.tagreferences.ItemTagReferences;/
		/^import net\.minecraft\.world\.item\.component\.ItemLore;/d' $M/NbtEditorGradients.java

	# mouse and keys came as numbers until 1.21.9, and the modifiers were asked to Screen
	sed -i 's/(s, click) -> DisplayAddon\.click(display, click\.x(), click\.y())/(s, mouseX, mouseY, button) -> DisplayAddon.click(display, mouseX, mouseY)/
		s/(s, key) -> !undoKey((DisplayScreen<?>) s, key)/(s, key, scancode, modifiers) -> !undoKey((DisplayScreen<?>) s, key, scancode)/
		s/undoKey(DisplayScreen<?> screen, KeyEvent key)/undoKey(DisplayScreen<?> screen, int key, int scancode)/
		s/InputConstants\.getKey(key)/InputConstants.getKey(key, scancode)/
		s/\bmc\.has\(Control\|Shift\|Alt\)Down()/Screen.has\1Down()/g
		s/screen\.resize(screen\.width, screen\.height);/screen.resize(mc, screen.width, screen.height);/
		/^import net\.minecraft\.client\.input\.KeyEvent;/d' $M/NbtEditorGradients.java
	sed -i 's/KeyEvent event, CallbackInfoReturnable/int key, int scancode, int modifiers, CallbackInfoReturnable/; /int key = event\.key();/d
		s/Minecraft\.getInstance()\.hasControlDown()/Screen.hasControlDown()/
		s/^import net\.minecraft\.client\.input\.KeyEvent;/import net.minecraft.client.gui.screens.Screen;/' $M/mixin/MultiLineTextFieldMixin.java
	sed -i 's/Minecraft\.getInstance()\.hasControlDown()/Screen.hasControlDown()/g
		s/^import net\.minecraft\.client\.Minecraft;/&\nimport net.minecraft.client.gui.screens.Screen;/
		s/matrices\.translate(0\.0F, inside\.getScroll(), 0\.0F)/matrices.translate(0.0, inside.getScroll(), 0.0)/' $M/DisplayAddon.java
	# what is drawn last is not on top yet: depth decides, and NBT Editor draws the name field one step above the rest
	sed -i 's/^\tpublic static void render(DisplayScreen<?> screen, PoseStack matrices, int mouseX, int mouseY, float delta) {$/&\n\t\tmatrices.pushPose();\n\t\tmatrices.translate(0.0, 0.0, 2.0);/
		s/^\t\tif (panel != null) panel\.render(matrices, mouseX, mouseY, delta);$/&\n\t\tmatrices.popPose();/' $M/DisplayAddon.java
	sed -i 's/mouseClicked(MouseButtonEvent click, boolean doubled)/mouseClicked(double mouseX, double mouseY, int button)/
		/double mouseX = click\.x(), mouseY = click\.y();/d
		s/super\.mouseClicked(click, doubled)/super.mouseClicked(mouseX, mouseY, button)/
		s/mouseDragged(MouseButtonEvent click, double deltaX, double deltaY)/mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY)/
		s/super\.mouseDragged(click, deltaX, deltaY)/super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)/
		s/mouseReleased(MouseButtonEvent click)/mouseReleased(double mouseX, double mouseY, int button)/
		s/super\.mouseReleased(click)/super.mouseReleased(mouseX, mouseY, button)/
		s/click\.x()/mouseX/g; s/click\.y()/mouseY/g; s/click\.button()/button/g
		/^import net\.minecraft\.client\.input\.MouseButtonEvent;/d' $M/GradientPanel.java

	case $v in 1.17.1|1.18.2|1.19.4|1.20.1)
		# the wheel only turned one way until 1.20.2
		sed -i 's/mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY)/mouseScrolled(double mouseX, double mouseY, double scrollY)/
			s/super\.mouseScrolled(mouseX, mouseY, scrollX, scrollY)/super.mouseScrolled(mouseX, mouseY, scrollY)/' $M/GradientPanel.java
	;; esac

	case $v in 1.17.1|1.18.2)
		# the place of a widget was two fields until 1.19.3
		sed -i 's/\b\(menu\|button\)\.get\([XY]\)()/\1.\L\2/g; s/widget\.set\([XY]\)(\(.*\));/widget.\L\1\E = \2;/' $M/GradientPanel.java $M/DisplayAddon.java
	;; esac
}

for v in ${@:-$all $legacy}; do
	d=versions/$v/src
	rm -rf $d && cp -r src $d
	case " $legacy " in *" $v "*) legacy $v; continue;; esac
	T=$d/gametest/java/com/imnot/nbteditorgradients/gametest/NbtEditorGradientsClientTest.java
	java=21; nbte=2.0.9
	case $v in 26.*) java=25; nbte=2.0.11;; esac
	sed -i "s/\"minecraft\": \"~1.21.11\"/\"minecraft\": \"~$v\"/; s/\"java\": \">=21\"/\"java\": \">=$java\"/; s/\"nbteditor\": \">=2.0.9\"/\"nbteditor\": \">=$nbte\"/" $d/main/resources/fabric.mod.json
	sed -i "s/JAVA_21/JAVA_$java/" $d/main/resources/nbteditorgradients.mixins.json

	case $v in 1.21.10)
		# a screen is still handed the game when it is resized
		sed -i 's/screen\.resize(screen\.width, screen\.height);/screen.resize(Minecraft.getInstance(), screen.width, screen.height);/' $d/main/java/com/imnot/nbteditorgradients/NbtEditorGradients.java
	;; esac

	case $v in 26.*)
		# NBT Editor 2.0.11 took the names of Mojang for its own text fields, and gave its helper for identifiers another name
		find $d -name '*.java' | xargs sed -i 's/FormattedTextFieldWidget/FormattedEditBox/g; s/MultiLineTextFieldWidget/MultiLineEditBox/g; s/NamedTextFieldWidget/NamedEditBox/g; s/MVDrawableHelper.getDrawContext(/MVDrawableHelper.getGuiGraphics(/; s/\bIdentifierInst\b/ResourceLocationInst/g'
	;; esac

	case $v in 26.*)
		sed -i 's/mc.resizeDisplay();/mc.resizeGui();/' $T
	;; esac

	case $v in 26.3)
		# no tinyfd any more: the file windows are those of SDL
		cp port/FileDialogs-26.3.java $d/main/java/com/imnot/nbteditorgradients/FileDialogs.java
	;; esac

	case $v in 26.2|26.3)
		# the screen is held by the Gui
		sed -i 's/mc\.screen\b/mc.gui.screen()/g' $T
	;; esac
done
