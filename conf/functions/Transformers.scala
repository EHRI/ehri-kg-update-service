// Copied from https://github.com/EHRI/ehri-kg-mapping/blob/91d88bb67a7da2a346ad40f8f5438cd7a2904524/src/ShExMLTemplates/functions/Transformers.scala
// so the mappings do not fetch it at runtime. Changes made here should also go upstream.
class Transformers {

    def toLowerCase(input: String): String = {
        input.toLowerCase()
    }

    def toBeginningDateID(mainEntityID: String, dateID: String): String = {
        mainEntityID + "-" + dateID + "-beginningDate"
    }

    def toEndDateID(mainEntityID: String, dateID: String): String = {
        mainEntityID + "-" + dateID + "-endDate"
    }

    def removeSquareBrackets(input: String): String = {
        input.replaceFirst("\\[", "").reverse.replaceFirst("\\]", "").reverse
    }

    def removeCBPrefix(value: String): String = {
        value.replaceFirst("ehri_cb-", "")
    }

    def combinedPathInstitutionLocation(institutionID: String, index: String): String = {
        institutionID + "/locations/" + (index.toInt + 1)
    }

    def combinedPathInstitutionAddress(institutionID: String, index: String): String = {
        institutionID + "/addresses/" + (index.toInt + 1)
    }

    def combinedPathInstitutionCoordinates(institutionID: String): String = {
        institutionID + "/coordinates/1"
    }

    def combinedPathInstitutionMandates(institutionID: String): String = {
        institutionID + "/mandates/1"
    }

    def combinedPathHoldingAcquisition(holdingID: String): String = {
        holdingID + "/acquisitions/1"
    }

    def combinedPathHoldingAlternateIDs(holdingID: String, index: String): String = {
        holdingID + "/alternateIDs/" + (index.toInt + 1)
    }

    def combinedPathHistoricalAgentLegalStatus(historicalAgentID: String): String = {
        historicalAgentID + "/legalStatuses/1"
    }

    def combinedPathHistoricalAgentOccupation(historicalAgentID: String): String = {
        historicalAgentID + "/occupations/1"
    }

    def combinedPathHistoricalAgentFunction(historicalAgentID: String, index: String): String = {
        historicalAgentID + "/functions/" + (index.toInt + 1)
    }

    def combinedPathHistoricalAgentMandate(historicalAgentID: String, index: String): String = {
        historicalAgentID + "/mandates/" + (index.toInt + 1)
    }

    def recordOrRecordSet(levelOfDescription: String, itemCount: Int): String = {
        if(levelOfDescription == "item" && itemCount == 0) "Record" else "RecordSet"
    }

    def getRecordSetType(levelOfDescription: String): String = {
        if(levelOfDescription == "fonds") "Fonds"
        else if(levelOfDescription == "series") "Series"
        else if(levelOfDescription == "collection") "Collection"
        else if(levelOfDescription == "file") "File"
        else ""
    }

    def getEHRIDefinedRecordSetType(levelOfDescription: String): String = {
        if(levelOfDescription == "subfonds") "Subfonds"
        else if(levelOfDescription == "subseries") "Subseries"
        else if(levelOfDescription == "recordgrp") "RecordGroup"
        else if(levelOfDescription == "subgrp") "Subgroup"
        else if(levelOfDescription == "subcollection") "Subcollection"
        else if(levelOfDescription == "item") "Item"
        else if(levelOfDescription == "otherlevel") "OtherLevel"
        else ""
    }

    // Added locally, from the former conf/vocabularies/Transformers.scala
    def generateVocabUri(vocabularyId: String, itemId: String): String = {
        s"$vocabularyId/$itemId"
    }
}
