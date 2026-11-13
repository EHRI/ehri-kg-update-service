class EHRIFunctions {

  // The path, relative to http://lod.ehri-project-test.eu/vocabularies/, of a historical agent or concept: its
  // Portal id's vocabulary part, with '-' for '_', then the rest of the id, e.g. ehri_pers-000001 -> ehri-pers/000001.
  // An id without a '-' gives its whole id as the vocabulary part, and nothing after the '/'.
  // Keep in step with vocabularyPath in the service code.
  def vocabularyPath(id: String): String = {
    val (vocabulary, rest) = id.span(_ != '-')
    vocabulary.replace('_', '-') + "/" + rest.drop(1)
  }

  // Paths of an entity's other and parallel names. Some names have control characters, which IRIs can't, e.g. the
  // MARC non-sorting markers U+0098 and U+009C around "von", so they are left out; line breaks and tabs become spaces.
  def entityOtherNamePath(id: String, name: String): String = s"$id/otherNames/${iriName(name)}"
  def entityParallelNamePath(id: String, name: String): String = s"$id/parallelNames/${iriName(name)}"
  private def iriName(name: String): String = name.replaceAll("[\\t\\n\\r]", " ").replaceAll("\\p{Cc}", "")

  // Paths of the nodes belonging to a historical agent
  def otherNamePath(id: String, name: String): String = entityOtherNamePath(vocabularyPath(id), name)
  def parallelNamePath(id: String, name: String): String = entityParallelNamePath(vocabularyPath(id), name)
  def legalStatusPath(id: String): String = s"${vocabularyPath(id)}/legalStatuses/1"
  def occupationPath(id: String): String = s"${vocabularyPath(id)}/occupations/1"
  def functionPath(id: String, index: String): String = s"${vocabularyPath(id)}/functions/${index.toInt + 1}"
  def mandatePath(id: String, index: String): String = s"${vocabularyPath(id)}/mandates/${index.toInt + 1}"

  // Date periods, the Portal's DatePeriods: a start and an end date, either of which may be missing, and a precision
  // (year, quarter, month, week or day), which is often missing. ShExML leaves out null fields, which would misalign
  // the arguments of a function taking several, so a period is passed whole, as JsonPath gives it, e.g.
  // {startDate=1941, endDate=null, precision=year}.
  // A period's value is an ISO 8601/EDTF date or interval: a single date when both ends are the same ("1943"),
  // otherwise an interval, with nothing for an unknown end ("1941/1944", "1945/", "/1944-03-24"). Dates are given to
  // the period's precision when it's a year or month, and otherwise as they are. A period with a date that isn't an
  // ISO 8601 date with a four-digit year, which EDTF would need otherwise, is left out, e.g. the typo 190000-12-31.
  private val periodField = """(\w+)=([^,}]*)""".r
  private val isoDate = """(\d{4})(?:-(\d{2})(?:-(\d{2}))?)?""".r
  private def periodDates(period: String): (Option[String], Option[String]) = {
    val fields = periodField.findAllMatchIn(period).map(m => m.group(1) -> m.group(2).trim)
      .filter { case (_, value) => value.nonEmpty && value != "null" }.toMap
    def toPrecision(date: String): String = fields.get("precision") match {
      case Some("year") => date.split('-').take(1).mkString("-")
      case Some("month") => date.split('-').take(2).mkString("-")
      case _ => date
    }
    val dates = (fields.get("startDate").map(toPrecision), fields.get("endDate").map(toPrecision))
    if (List(dates._1, dates._2).flatten.forall(isoDate.pattern.matcher(_).matches)) dates else (None, None)
  }

  def hasPeriod(period: String): Boolean = periodDates(period) != (None, None)
  def periodValue(period: String): String = periodDates(period) match {
    case (Some(start), Some(end)) if start == end => start
    case (start, end) => start.getOrElse("") + "/" + end.getOrElse("")
  }

  // A person's period is their life, so its start and end are their birth and death dates
  def hasStartDate(period: String): Boolean = periodDates(period)._1.isDefined
  def hasEndDate(period: String): Boolean = periodDates(period)._2.isDefined
  def startDate(period: String): String = periodDates(period)._1.getOrElse("")
  def endDate(period: String): String = periodDates(period)._2.getOrElse("")

  // Paths of an entity's rico:Date nodes, one for each of its periods, or for a person one for each birth and death
  // date, named after their values with '_' for '/', e.g. units/<id>/dates/1941_1944. Naming them after their values
  // rather than numbering them means that the same period in several descriptions of an entity is one node.
  def entityPeriodPath(id: String, period: String): String = datePath(id, periodValue(period))
  def entityStartDatePath(id: String, period: String): String = datePath(id, startDate(period))
  def entityEndDatePath(id: String, period: String): String = datePath(id, endDate(period))
  def periodPath(id: String, period: String): String = entityPeriodPath(vocabularyPath(id), period)
  private def datePath(path: String, date: String): String = s"$path/dates/${date.replace('/', '_')}"

  // Bounds, for range queries: the earliest and latest day a date can be, as xsd:dates, e.g. 1942 is 1942-01-01 to
  // 1942-12-31, and 1944-02 ends on 1944-02-29. A period's are the earliest day its start can be and the latest day
  // its end can be; a birth date's are those of the period's start, and a death date's those of its end. An unknown
  // date, or one that isn't a real date, such as 1944-02-30, has no bound.
  private def bound(date: Option[String], latest: Boolean): Option[String] = date.flatMap {
    case isoDate(year, month, day) => scala.util.Try {
      val y = year.toInt
      val d = (Option(month), Option(day)) match {
        case (Some(m), Some(dd)) => java.time.LocalDate.of(y, m.toInt, dd.toInt)
        case (Some(m), None) =>
          val yearMonth = java.time.YearMonth.of(y, m.toInt)
          if (latest) yearMonth.atEndOfMonth else yearMonth.atDay(1)
        case _ => if (latest) java.time.LocalDate.of(y, 12, 31) else java.time.LocalDate.of(y, 1, 1)
      }
      d.toString
    }.toOption
    case _ => None
  }
  def hasEarliestDate(period: String): Boolean = bound(periodDates(period)._1, latest = false).isDefined
  def hasLatestDate(period: String): Boolean = bound(periodDates(period)._2, latest = true).isDefined
  def earliestDate(period: String): String = bound(periodDates(period)._1, latest = false).getOrElse("")
  def latestDate(period: String): String = bound(periodDates(period)._2, latest = true).getOrElse("")
  def hasLatestStartDate(period: String): Boolean = bound(periodDates(period)._1, latest = true).isDefined
  def hasEarliestEndDate(period: String): Boolean = bound(periodDates(period)._2, latest = false).isDefined
  def latestStartDate(period: String): String = bound(periodDates(period)._1, latest = true).getOrElse("")
  def earliestEndDate(period: String): String = bound(periodDates(period)._2, latest = false).getOrElse("")

  // Kinds of item an access point can link to. The service leaves out access points to agents and concepts whose
  // authority sets and vocabularies aren't in the KG before mapping (see KgSets in the service code).
  def isAgent(itemType: String): Boolean = itemType == "HistoricalAgent"
  def isAgentOrConcept(itemType: String): Boolean = itemType == "HistoricalAgent" || itemType == "CvocConcept"

}
