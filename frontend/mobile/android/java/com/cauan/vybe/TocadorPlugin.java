package com.cauan.vybe;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

/**
 * Ponte entre o player (JavaScript, services/tocadorNativo.js) e o TocadorService.
 *
 * atualizar({titulo, artista, tocando, fundo}) mostra ou atualiza a notificação;
 * parar() tira a notificação. Os botões da notificação voltam como o evento "acao".
 */
@CapacitorPlugin(name = "Tocador")
public class TocadorPlugin extends Plugin {

    private static volatile TocadorPlugin instancia;

    @Override
    public void load() {
        instancia = this;
    }

    @Override
    protected void handleOnDestroy() {
        if (instancia == this) {
            instancia = null;
        }
        // App fechado de vez: sem o WebView não há o que tocar.
        TocadorService.parar(getContext());
    }

    @PluginMethod
    public void atualizar(PluginCall call) {
        String titulo = call.getString("titulo", "VYBE");
        String artista = call.getString("artista", "");
        boolean tocando = Boolean.TRUE.equals(call.getBoolean("tocando", false));
        boolean fundo = Boolean.TRUE.equals(call.getBoolean("fundo", true));
        TocadorService.atualizar(getContext(), titulo, artista, tocando, fundo);
        call.resolve();
    }

    @PluginMethod
    public void parar(PluginCall call) {
        TocadorService.parar(getContext());
        call.resolve();
    }

    /** Um botão da notificação (ou do fone Bluetooth) foi apertado: "anterior", "alternar" ou "proxima". */
    static void avisar(String acao) {
        TocadorPlugin plugin = instancia;
        if (plugin == null) {
            return;
        }
        JSObject dados = new JSObject();
        dados.put("acao", acao);
        plugin.notifyListeners("acao", dados);
    }
}
