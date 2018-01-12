package com.serena.air.octane

enum EntityType {

    DEFECT,
    STORY,
    WORK_ITEM,
    COMMENT,
    UNKNOWN,
    NOT_FOUND;

    public static EntityType getType(String method) {
        switch (method) {
            case 'DEFECT': return DEFECT
            case 'STORY': return STORY
            case 'WORK_ITEM': return WORK_ITEM
            case 'COMMENT': return COMMENT
            case 'UNKNOWN': return UNKNOWN
            case 'NOT_FOUND': return NOT_FOUND
        }
    }
}