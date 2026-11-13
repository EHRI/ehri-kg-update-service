// Copied from https://github.com/EHRI/ehri-kg-mapping/blob/91d88bb67a7da2a346ad40f8f5438cd7a2904524/src/ShExMLTemplates/functions/Validators.scala
// so the mappings do not fetch it at runtime. Changes made here should also go upstream.
class Validators {
    
    def nonEmpty(str: String): Boolean = {
        str.nonEmpty
    }

    def isFamily(cbType: String): Boolean = {
        cbType == "family"
    }

    def isCB(cbType: String): Boolean = {
        !isFamily(cbType)
    }

    def isURL(input: String): Boolean = {
        try {
            // For the moment this allows to parse the output
            val processedInput = input.replaceFirst("\\[", "").reverse.replaceFirst("\\]", "").reverse
            new java.net.URL(processedInput).toURI()
            true
        } catch {
            case e: java.net.MalformedURLException => false
            case ue: java.net.URISyntaxException => false
            case _ => false
        }
    }

    def nonURL(input: String): Boolean = {
        !isURL(input)
    }

    def areSourceAndTargetDifferentAndIsSubjectInstitution(sourceId: String, targetId: String, sourceType: String): Boolean = {
        sourceId != targetId && sourceType == "Repository"
    }

    def areSourceAndTargetDifferentAndIsSubjectDocumentaryUnit(sourceId: String, targetId: String, sourceType: String): Boolean = {
        sourceId != targetId && sourceType == "DocumentaryUnit"
    }

    def isDocumentaryUnit(theType: String): Boolean = {
        theType == "DocumentaryUnit"
    }

    def isInstitution(theType: String): Boolean = {
        theType == "Repository"
    }

    def hasCBPrefix(value: String): Boolean = {
        value.startsWith("ehri_cb-")
    }
    
    def nonEmptyTwo(id: String, attribute: String): Boolean = {
        id.nonEmpty && attribute.nonEmpty
    }

     def isRecordSetTypeDefinedInRiC(levelOfDescription: String): Boolean = {
        levelOfDescription == "fonds" ||
        levelOfDescription == "series" ||
        levelOfDescription == "collection" ||
        levelOfDescription == "file" 
    }

    def isRecordSetTypeDefinedInEHRI(levelOfDescription: String): Boolean = {
        levelOfDescription == "subfonds" ||
        levelOfDescription == "subseries" ||
        levelOfDescription == "recordgrp" ||
        levelOfDescription == "subgrp" ||
        levelOfDescription == "subcollection" ||
        levelOfDescription == "item" ||
        levelOfDescription == "otherlevel"
    }

    // Added locally, from the former conf/vocabularies/Validators.scala
    def greaterThanZero(value: String): Boolean = value.toFloat > 0
}
