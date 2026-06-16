package com.aulahub.backend.model.enums;

// Un enum es un tipo especial de clase que representa un conjunto fijo de constantes
// En lugar de guardar "PROFESOR", "ADMIN" etc. como Strings libres en el código,
// el enum garantiza que solo puedas usar valores válidos y predefinidos

public enum TipoRol {
    PROFESOR,
    ADMIN,
    SUPER_ADMIN,
    ADMIN_CENTRAL
}

