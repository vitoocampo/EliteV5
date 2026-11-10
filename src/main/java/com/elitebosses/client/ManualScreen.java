package com.elitebosses.client;

import com.elitebosses.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Manual de Supervivencia (v4): indice con capitulos, encabezado de color por capitulo,
 * titulos, vinetas, datos clave, avisos y trucos. Flechas del teclado o botones para pasar de pagina.
 *
 * Formato del texto de cada capitulo (una linea por renglon):
 *   "# Titulo"   subtitulo       "- texto"   vineta        ". texto"   parrafo
 *   "= Dato|valor"  dato clave   "! texto"   aviso (rojo)  "> texto"   truco (verde)   ""  espacio
 */
public class ManualScreen extends Screen {
    private static final int PAGE_W = 320;
    private static final int PAGE_H = 220;
    private static final int MARGIN = 16;
    private static final int TEXT_W = PAGE_W - MARGIN * 2;
    private static final int HEADER_H = 24;
    private static final int BODY_TOP = HEADER_H + 8;
    private static final int BODY_H = PAGE_H - BODY_TOP - 30;
    private static final int INDEX_TOP = HEADER_H + 6;   // primera fila del indice
    private static final int ROW_PITCH = 21, ROW_H = 19; // filas del indice

    private static final int PAPER = 0xFFEFE3C2;
    private static final int PAPER_SHADE = 0xFFE3D3A8;
    private static final int INK = 0x2A1A0A;

    private record Chapter(String title, String blurb, Supplier<ItemLike> icon, int color, String body, boolean recipe) { }

    private record Line(FormattedCharSequence text, int height, int x, boolean bullet, boolean spacer, boolean heading) { }

    private record Page(int chapter, List<Line> lines, boolean recipe) { }

    private static Chapter ch(String title, String blurb, Supplier<ItemLike> icon, int color, String body) {
        return new Chapter(title, blurb, icon, color, body, false);
    }

    private static final List<Chapter> CHAPTERS = List.of(
        ch("Guía rápida", "Qué hacer ante cada jefe", () -> Items.WRITABLE_BOOK, 0x2F5D8A,
            ". Lo esencial para sobrevivir. El detalle de cada jefe está en los siguientes capítulos.\n"
          + "# Chocolate\n"
          + "> Come una galleta cerca de él (32 bloques o menos): desaparece.\n"
          + "- Con 5 corazones o menos de vida, pasa a su fase 2.\n"
          + "! No bebas leche: lo espanta, pero después de 10 s de ceguera mueres.\n"
          + "# Sabueso\n"
          + "- No puedes huir: es más rápido que tú. Prepárate antes de que llegue.\n"
          + "- Pulsa ESPACIO para zafarte cuando te agarre.\n"
          + "- Mantén más de 6 corazones: con menos activa el REMATE.\n"
          + "- Alza el escudo durante el REMATE: lo aguanta.\n"
          + "> Una cama a menos de 60 bloques evita que rompa bloques.\n"
          + "# Krasue\n"
          + "- Su Obsesión dura 5 minutos; después despierta.\n"
          + "- Vigila tu Salmuera: con 50 cargas la Ejecución te mata.\n"
          + "> La leche quita la Salmuera y evita la Ejecución.\n"
          + "# Si todo sale mal\n"
          + "> El Angel Guardian hace desaparecer a todos los jefes a 128 bloques y te da Velo Diurno 20 minutos."),
        ch("Chocolate", "Fases, furia y cómo pararlo", () -> ModItems.CHOCOLATE_SPAWN_EGG.get(), 0x6B3A1E,
            "# Datos\n"
          + "= Vida|100 corazones (200)\n"
          + "= Objetivo|el jugador survival más cercano, a 32 bloques o menos\n"
          + "= Música|suena a 32 bloques\n"
          + "= Drop|1 Tótem de la Inmortalidad\n"
          + "# Fase 1\n"
          + "- Algo más lento que tú corriendo.\n"
          + "- Cada golpe lo hace un 20% más rápido y le suma 1 de daño (empieza en 2 corazones).\n"
          + "- Su espera entre golpes se reduce a la mitad con cada golpe (mínimo 0.2 s).\n"
          + "- Pasa a la fase 2 cuando tu vida baja a 5 corazones o menos, o cuando a él le quedan 25 corazones o menos.\n"
          + "# Fase 2\n"
          + "- Te alcanza y te deja en 1 corazón.\n"
          + "- Se aleja 15 bloques y te da Velocidad II durante 4:30.\n"
          + "- Espera 5 s, mantiene 7-9 bloques de distancia 10 s, te sigue lento 1 min y luego más rápido.\n"
          + "- Los últimos 30 s te mata a golpes de 3 corazones (1 s entre golpes).\n"
          + "- Si se acaba tu Velocidad II, desaparece.\n"
          + "# Furia\n"
          + ". Con 5 corazones de vida o menos se mueve al doble de velocidad.\n"
          + "# Cómo pararlo\n"
          + "> GALLETA: si tú (o quien te mira) terminas de comer una galleta a 32 bloques o menos, desaparece con gritos de ghast, sin soltar nada. También desaparece el que te persigue a ti sin importar la distancia.\n"
          + "! LECHE: lo hace desaparecer igual y te quita los efectos... pero paga el tramposo: 10 s de ceguera, ruidos y textos, y después mueres."),
        ch("Sabueso", "Velocidad, ataques y Remate", () -> ModItems.HOUNDMASTER_SPAWN_EGG.get(), 0x8B1A1A,
            "# Datos\n"
          + "= Vida|700 corazones (1400). No regenera de ninguna forma\n"
          + "= Búsqueda|8 bloques/s cuando el jugador más cercano está a más de 70 bloques\n"
          + "= Persecución|~13.8 bloques/s cuando está a 70 bloques o menos\n"
          + "= Música|HoundLurk a 120 bloques, HoundChase a 70\n"
          + "- Va tras el jugador survival más cercano sin importar la distancia.\n"
          + "- Rompe bloques naturales en su camino (no cerca de camas ni si mobGriefing está apagado).\n"
          + "- Si mata a un jugador y no hay otros a 90 bloques, desaparece sin drop. Si los hay, duplica su velocidad de persecución hasta su siguiente golpe.\n"
          + "- Con 3-4 jugadores a 60 bloques hace un 10% menos de daño; con 5 o más, un 15% más.\n"
          + "# Abalanzar\n"
          + ". Salta y te muerde: 7 de daño (3.5 corazones) antes de armadura.\n"
          + "# Levantar\n"
          + ". Gruñe y te lanza unos 10 bloques al aire.\n"
          + "# Agarre\n"
          + ". Te sujeta: pulsa ESPACIO 5-25 veces para zafarte. Cada segundo hace 3.5 de daño y desgasta tu armadura (el hierro se rompe a los 5 s).\n"
          + "# Manada\n"
          + ". Aulla y llama 2-5 lobos que duran 15 s. Mientras tanto recibe un 10% más de daño 5 s. Con 3 lobos o menos, estos se potencian y todos los jugadores a 64 bloques reciben Hambre IV.\n"
          + "# Retirada\n"
          + ". Con poca vida puede huir y llamar 2 lobos extra.\n"
          + "# Canalización\n"
          + "- Cada 30 s en combate tiene un 15% de usarla. Baja la cabeza 5 s y aparece \"El Sabueso escarba\".\n"
          + "- Invoca un LOBO HOSTIL de 50 corazones y Velocidad 10 que dura 15 s. Cada golpe da Lentitud 5 por 5 s.\n"
          + "- Cada 3 s tiene un 50% de agarrarte (no lo hace con menos de 6 corazones): pulsa ESPACIO 30-50 veces.\n"
          + "- Si solo hay un jugador a 64 bloques del Sabueso, te arrastra hacia él; si hay más, te aleja y te mantiene a 30 bloques.\n"
          + "# REMATE\n"
          + "- Se lanza cuando un jugador tiene menos de 6 corazones. Su único enfriamiento: haber usado 2 ataques especiales desde el último. Con menos vida, sus ataques especiales son más seguidos.\n"
          + "- No se puede detener. Aulla, embiste y: 65% acierta / 35% falla.\n"
          + "- FALLA (\"REMATE FALLIDO\"): se aturde 3 s y pierde 20 de vida.\n"
          + "- ACIERTA: un escudo alzado aguanta (se rompe y quedas con 1 corazón). Si no, te lanza; chocar con una estructura es muerte instantánea.\n"
          + "! Si mueres así (\"REMATE ASESTADO\"), todos a 64 bloques sufren Ceguera 20 s, Hambre 10 por 7 s, Lentitud 1 min y Oscuridad 7 s.\n"
          + "= Drop|15 huesos, 5 diamantes, 2 manzanas de oro, 1 manzana encantada, 3 tótems"),
        ch("Krasue (Stalker)", "Obsesión, etapas y ataques", () -> ModItems.STALKER_SPAWN_EGG.get(), 0x5B2A86,
            ". La Krasue, espíritu de la mitología tailandesa. Tiene dos etapas.\n"
          + "# Etapa 1: la Obsesión\n"
          + ". Tamaño de Allay, vuela, es INMORTAL y no hace daño. Elige al jugador survival más cercano, le da OBSESIÓN (5 min) y solo lo persigue a él.\n"
          + "= 0:00 - 2:00|a 32 bloques; parpadea invisible (4 s) / visible (0.8 s)\n"
          + "= 2:00 - 3:00|a 16 bloques; suena StalkerStalk1\n"
          + "= 3:00 - 4:00|a 8 bloques; suena StalkerStalk2\n"
          + "= 4:00 - 5:00|invisible; Ceguera 30 s, Náusea y pierdes 0.5 corazones cada 4 s (ignora armadura)\n"
          + "! Los últimos 30 s tu pantalla se oscurece y parpadea entre negro y destellos blancos y negros translúcidos, con sonidos de carne.\n"
          + "# Etapa 2: despierta\n"
          + "= Vida|500 corazones; 3 bloques de alto\n"
          + "= Vuelo|a 3-8 bloques de altura; solo te persigue a ti hasta que alguno muera\n"
          + "= Velocidad|la de un lobo, +1 nivel de Velocidad cada 2 min hasta Velocidad 4\n"
          + "= Música|StalkerChase\n"
          + "- Ya no tiene Resistencia: recibe el daño completo.\n"
          + "- Con menos de 30% de vida, sus ataques dan Hambre 10 por 2 s.\n"
          + "# Látigo\n"
          + ". A distancia: 10 de daño (5 corazones) y +1 Salmuera.\n"
          + "# Ascensión visceral\n"
          + ". Marca el suelo 4x4; a los 2 s surgen espinas: 15 de daño y +2 Salmuera. Con 5+ cargas de Salmuera el área es 8x8 y da +4.\n"
          + "# Rapto visceral\n"
          + "- Te encierra con ella en una celda de barras de hierro (~4x4) durante 15 s; +1 Salmuera.\n"
          + "- Empieza a azotarte con látigos: 3 de daño cada segundo (1.5 corazones). La armadura lo reduce.\n"
          + "- Si ya tienes 15 de Salmuera, el suelo es lava (ella es inmune).\n"
          + "# Purga\n"
          + ". Se eleva 8 bloques y canaliza 30 s (\"Purgando\"). Solo si tienes 20+ de Salmuera y menos de 8 corazones. Borra toda tu Salmuera y recibes 2 de daño por carga. Recibir mucho daño la interrumpe.\n"
          + "# Ejecución\n"
          + ". Canaliza 10 s sin poder detenerla (\"INFECCIÓN INMINENTE\"). Si al final tienes 50 de Salmuera, mueres al instante.\n"
          + "> Beber leche la evita.\n"
          + "# Drenaje\n"
          + ". Con menos de 40% de vida y 5+ de Salmuera, te inmoviliza: pulsa ESPACIO 5-10 veces. Cada segundo ella cura 4 corazones y tú pierdes 1 de Salmuera.\n"
          + "# Final\n"
          + "- Cuando mata a su objetivo, aterriza, canaliza 8 s y desaparece.\n"
          + "- Si la matas durante ese rezo: 2 tótems.\n"
          + "- Si la matas antes de que termine su trabajo: 1 manzana de oro encantada y 3 tótems."),
        ch("Efectos", "Obsesión, Salmuera, Velo Diurno", () -> Items.POTION, 0x1F7A6B,
            ". Los efectos de Elite Bosses muestran su nivel con número (1, 2, 3... hasta 200), no en romanos.\n"
          + "# Obsesión\n"
          + ". Dura 5 minutos. Marca al jugador elegido por la Krasue. Si la quitas con leche, ella te la vuelve a poner con el tiempo restante.\n"
          + "# Salmuera\n"
          + ". Se acumula: cada carga sube el nivel (máximo 200). Dura hasta que mueres, la Krasue muere o bebes leche.\n"
          + "= 5 cargas|Ascensión de 8x8 (+4) y la Krasue puede usar Drenaje\n"
          + "= 15 cargas|el suelo del Rapto visceral es lava\n"
          + "= 20 cargas|la Purga es posible (con menos de 8 corazones)\n"
          + "= 50 cargas|la Ejecución es muerte al instante\n"
          + "# Velo Diurno\n"
          + ". Lo da el Angel Guardian y dura 20 minutos. Mientras lo tengas, cualquier mob de Elite Bosses que se acerque a 15 bloques o menos de ti desaparece.\n"
          + "! Como cualquier efecto de poción, la leche lo quita."),
        ch("Objetos", "Angel Guardian, Tótem, Manual", () -> Items.TOTEM_OF_UNDYING, 0x9A7A12,
            "# Angel Guardian\n"
          + ". Al usarlo, todo mob de Elite Bosses a 128 bloques desaparece sin soltar nada, y recibes Velo Diurno durante 20 minutos. Se consume al usarlo. La receta está en la página siguiente.\n"
          + "# Tótem BlackFate\n"
          + "- Lo sueltan los esqueletos Wither con un 20% de probabilidad.\n"
          + "- Funciona como un tótem normal, pero vacía tu barra de hambre y te cura y otorga un 30% menos que el original (regeneración, resistencia al fuego y absorción reducidas).\n"
          + "# Manual de Supervivencia\n"
          + ". Solo se consigue con aldeanos bibliotecarios, a cambio de 40 diamantes."),
        new Chapter("Receta: Angel Guardian", "Cómo fabricarlo", () -> ModItems.VITOS_BLESSING.get(), 0xB8893B, "", true)
    );

    private final List<Page> pages = new ArrayList<>();
    private final int[] chapterStart = new int[CHAPTERS.size()];
    private int page = 0;

    public ManualScreen() {
        super(Component.literal("Manual de Supervivencia"));
    }

    public static void open() {
        Minecraft.getInstance().setScreen(new ManualScreen());
    }

    // ------------------------------------------------------------------ maquetacion
    private List<Line> layout(Chapter chapter) {
        List<Line> out = new ArrayList<>();
        Style heading = Style.EMPTY.withBold(true).withColor(TextColor.fromRgb(chapter.color()));
        for (String raw : chapter.body().split("\n", -1)) {
            if (raw.isEmpty()) {
                out.add(new Line(FormattedCharSequence.EMPTY, 5, 0, false, true, false));
                continue;
            }
            char mark = raw.charAt(0);
            String txt = raw.length() > 2 ? raw.substring(2) : "";
            MutableComponent comp;
            int indent = 0;
            boolean bullet = false;
            boolean isHeading = false;
            if (mark == '#') {
                out.add(new Line(FormattedCharSequence.EMPTY, 4, 0, false, true, false));
                comp = Component.literal(txt).withStyle(heading);
                isHeading = true;
            } else if (mark == '-') {
                comp = Component.literal(txt);
                indent = 9;
                bullet = true;
            } else if (mark == '!') {
                comp = Component.literal("Cuidado: ").withStyle(Style.EMPTY.withBold(true).withColor(TextColor.fromRgb(0x9B1B1B)))
                        .append(Component.literal(txt).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0x6B1111))));
            } else if (mark == '>') {
                comp = Component.literal("Truco: ").withStyle(Style.EMPTY.withBold(true).withColor(TextColor.fromRgb(0x1F6B2A)))
                        .append(Component.literal(txt).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0x174F20))));
            } else if (mark == '=') {
                int bar = txt.indexOf('|');
                String label = bar < 0 ? txt : txt.substring(0, bar);
                String value = bar < 0 ? "" : txt.substring(bar + 1);
                comp = Component.literal(label + ": ").withStyle(Style.EMPTY.withBold(true).withColor(TextColor.fromRgb(0x7A4A00)))
                        .append(Component.literal(value));
            } else {
                comp = Component.literal(txt);
            }
            List<FormattedCharSequence> parts = this.font.split(comp, TEXT_W - indent);
            for (int i = 0; i < parts.size(); i++) {
                out.add(new Line(parts.get(i), isHeading ? 12 : 10, indent, bullet && i == 0, false, isHeading && i == 0));
            }
        }
        return out;
    }

    private void buildPages() {
        pages.clear();
        pages.add(new Page(-1, List.of(), false)); // indice
        for (int c = 0; c < CHAPTERS.size(); c++) {
            Chapter chapter = CHAPTERS.get(c);
            chapterStart[c] = pages.size();
            if (chapter.recipe()) {
                pages.add(new Page(c, List.of(), true));
                continue;
            }
            List<Line> current = new ArrayList<>();
            int used = 0;
            for (Line ln : layout(chapter)) {
                boolean overflow = used + ln.height() > BODY_H || (ln.heading() && used + ln.height() + 12 > BODY_H);
                if (overflow && !current.isEmpty()) {
                    pages.add(new Page(c, current, false));
                    current = new ArrayList<>();
                    used = 0;
                }
                if (current.isEmpty() && ln.spacer()) {
                    continue; // una pagina no empieza con espacio en blanco
                }
                current.add(ln);
                used += ln.height();
            }
            if (!current.isEmpty()) {
                pages.add(new Page(c, current, false));
            }
        }
    }

    @Override
    protected void init() {
        buildPages();
        page = Math.min(page, pages.size() - 1);
        int left = (this.width - PAGE_W) / 2, top = (this.height - PAGE_H) / 2;
        this.addRenderableWidget(Button.builder(Component.literal("<"), b -> goTo(page - 1))
                .bounds(left + 12, top + PAGE_H - 24, 26, 16).build());
        this.addRenderableWidget(Button.builder(Component.literal("Índice"), b -> goTo(0))
                .bounds(left + PAGE_W / 2 - 30, top + PAGE_H - 24, 60, 16).build());
        this.addRenderableWidget(Button.builder(Component.literal(">"), b -> goTo(page + 1))
                .bounds(left + PAGE_W - 38, top + PAGE_H - 24, 26, 16).build());
    }

    private void goTo(int target) {
        page = Math.max(0, Math.min(pages.size() - 1, target));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_RIGHT) {
            goTo(page + 1);
            return true;
        }
        if (key == GLFW.GLFW_KEY_LEFT) {
            goTo(page - 1);
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) {
            return true;
        }
        if (page == 0 && button == 0) {
            int left = (this.width - PAGE_W) / 2, top = (this.height - PAGE_H) / 2;
            for (int i = 0; i < CHAPTERS.size(); i++) {
                int x = left + MARGIN, y = top + INDEX_TOP + i * ROW_PITCH;
                if (mx >= x && mx < x + TEXT_W && my >= y && my < y + ROW_H) {
                    Minecraft.getInstance().getSoundManager().play(
                            SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.0F, 0.6F));
                    goTo(chapterStart[i]);
                    return true;
                }
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ dibujo
    @Override
    public void render(GuiGraphics gg, int mouseX, int mouseY, float partial) {
        this.renderBackground(gg);
        int left = (this.width - PAGE_W) / 2, top = (this.height - PAGE_H) / 2;
        drawBook(gg, left, top);
        if (!pages.isEmpty()) {
            Page pg = pages.get(page);
            if (pg.chapter() < 0) {
                drawIndex(gg, left, top, mouseX, mouseY);
            } else {
                Chapter chapter = CHAPTERS.get(pg.chapter());
                int inChapter = 1, total = 0;
                for (int i = 0; i < pages.size(); i++) {
                    if (pages.get(i).chapter() == pg.chapter()) {
                        total++;
                        if (i < page) {
                            inChapter++;
                        }
                    }
                }
                String title = total > 1 ? chapter.title() + " (" + inChapter + "/" + total + ")" : chapter.title();
                drawHeader(gg, left, top, title, chapter.color(), chapter.icon().get());
                if (pg.recipe()) {
                    drawRecipe(gg, left, top);
                } else {
                    int y = top + BODY_TOP;
                    for (Line ln : pg.lines()) {
                        if (ln.bullet()) {
                            gg.drawString(this.font, "•", left + MARGIN + 2, y, chapter.color(), false);
                        }
                        if (!ln.spacer()) {
                            gg.drawString(this.font, ln.text(), left + MARGIN + ln.x(), y, INK, false);
                        }
                        y += ln.height();
                    }
                }
            }
            String num = (page + 1) + " / " + pages.size();
            gg.drawString(this.font, num, left + PAGE_W - MARGIN - this.font.width(num), top + 8, 0xFFFFFF, true);
        }
        super.render(gg, mouseX, mouseY, partial);
    }

    private void drawBook(GuiGraphics gg, int left, int top) {
        gg.fill(left - 4, top - 4, left + PAGE_W + 4, top + PAGE_H + 4, 0xFF2E1B0B);   // cubierta
        gg.fill(left - 2, top - 2, left + PAGE_W + 2, top + PAGE_H + 2, 0xFF7A5A30);   // borde dorado
        gg.fill(left, top, left + PAGE_W, top + PAGE_H, PAPER);                          // pagina
        gg.fill(left, top, left + 5, top + PAGE_H, PAPER_SHADE);                         // lomo
        gg.fill(left + 5, top, left + 6, top + PAGE_H, 0xFFC9B27E);
        gg.fill(left + MARGIN, top + PAGE_H - 30, left + PAGE_W - MARGIN, top + PAGE_H - 29, 0xFFC9B27E); // linea del pie
    }

    private void drawHeader(GuiGraphics gg, int left, int top, String title, int color, ItemLike icon) {
        gg.fill(left, top, left + PAGE_W, top + HEADER_H, 0xFF000000 | darken(color));
        gg.fill(left, top + HEADER_H, left + PAGE_W, top + HEADER_H + 2, 0xFF000000 | color);
        gg.renderItem(new ItemStack(icon), left + 8, top + 4);
        gg.drawString(this.font, Component.literal(title).withStyle(net.minecraft.ChatFormatting.BOLD),
                left + 30, top + 8, 0xFFFFFF, true);
    }

    private static int darken(int rgb) {
        int r = ((rgb >> 16) & 0xFF) * 6 / 10, g = ((rgb >> 8) & 0xFF) * 6 / 10, b = (rgb & 0xFF) * 6 / 10;
        return (r << 16) | (g << 8) | b;
    }

    private void drawIndex(GuiGraphics gg, int left, int top, int mouseX, int mouseY) {
        drawHeader(gg, left, top, "Manual de Supervivencia", 0x7A5A30, ModItems.SURVIVAL_MANUAL.get());
        for (int i = 0; i < CHAPTERS.size(); i++) {
            Chapter chapter = CHAPTERS.get(i);
            int x = left + MARGIN, y = top + INDEX_TOP + i * ROW_PITCH;
            boolean hover = mouseX >= x && mouseX < x + TEXT_W && mouseY >= y && mouseY < y + ROW_H;
            gg.fill(x, y, x + TEXT_W, y + ROW_H, hover ? 0xFFD9C48C : PAPER_SHADE);
            gg.fill(x, y, x + 3, y + ROW_H, 0xFF000000 | chapter.color());
            gg.renderItem(new ItemStack(chapter.icon().get()), x + 7, y + 1);
            gg.drawString(this.font, Component.literal(chapter.title()).withStyle(
                    Style.EMPTY.withBold(true).withColor(TextColor.fromRgb(chapter.color()))), x + 28, y + 1, INK, false);
            gg.drawString(this.font, chapter.blurb(), x + 28, y + 10, 0x5A4020, false);
            String pn = "p. " + (chapterStart[i] + 1);
            gg.drawString(this.font, pn, x + TEXT_W - 6 - this.font.width(pn), y + 5, 0x5A4020, false);
        }
        gg.drawString(this.font, "Haz clic en un capítulo, o usa las flechas ← →", left + MARGIN,
                top + INDEX_TOP + CHAPTERS.size() * ROW_PITCH + 2, 0x5A4020, false);
    }

    private void drawRecipe(GuiGraphics gg, int left, int top) {
        ItemLike[][] grid = {
            {Items.TOTEM_OF_UNDYING, Items.GOLD_BLOCK, Items.TOTEM_OF_UNDYING},
            {Items.GOLDEN_APPLE, Items.WITHER_SKELETON_SKULL, Items.GOLDEN_APPLE},
            {Items.CRYING_OBSIDIAN, Items.SOUL_SAND, Items.CRYING_OBSIDIAN}};
        int gx = left + 48, gy = top + 44;
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                int x = gx + c * 22, y = gy + r * 22;
                gg.fill(x - 1, y - 1, x + 19, y + 19, 0xFF8B8B8B);
                gg.fill(x, y, x + 18, y + 18, 0xFFC6C6C6);
                gg.renderItem(new ItemStack(grid[r][c]), x + 1, y + 1);
            }
        }
        gg.drawString(this.font, "=>", gx + 74, gy + 26, INK, false);
        int x = gx + 100, y = gy + 22;
        gg.fill(x - 1, y - 1, x + 19, y + 19, 0xFF8B8B8B);
        gg.fill(x, y, x + 18, y + 18, 0xFFC6C6C6);
        gg.renderItem(new ItemStack(ModItems.VITOS_BLESSING.get()), x + 1, y + 1);
        gg.drawString(this.font, "Mesa de crafteo (3x3)", left + MARGIN, top + 116, 0x5A4020, false);
        gg.drawString(this.font, "Tótem / Bloque de oro / Tótem", left + MARGIN, top + 130, INK, false);
        gg.drawString(this.font, "Manzana de oro / Calavera de Wither / Manzana de oro", left + MARGIN, top + 142, INK, false);
        gg.drawString(this.font, "Obsidiana llorona / Arena de almas / Obsidiana llorona", left + MARGIN, top + 154, INK, false);
    }
}
