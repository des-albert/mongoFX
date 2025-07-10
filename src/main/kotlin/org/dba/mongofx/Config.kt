package org.dba.mongofx

import org.bson.codecs.pojo.annotations.BsonId
import org.bson.types.ObjectId

data class Config(
    @param:BsonId val id: ObjectId = ObjectId(),
    var ucid: String,
    var exportDate: String,
    var ope: String,
    var unique: String,
    var customer: String,
    var product: String,
    var fileName: String
)
