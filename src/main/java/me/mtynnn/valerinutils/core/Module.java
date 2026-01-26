package me.mtynnn.valerinutils.core;

public interface Module {

    // Id interno del módulo (para config)
    String getId();

    // Se llama cuando el plugin se habilita
    void enable();

    // Se llama cuando el plugin se deshabilita
    void disable();
}
