<!DOCTYPE html>
<%@ page language="java" contentType="text/html; charset=utf-8" pageEncoding="utf-8" %>


<html>
<head>
    <%@ include file="head.jsp" %>
    <%@ include file="jquery.jsp" %>
    <script src="<c:url value='/script/subsonic-database-status.js'/>"></script>
    <script src="<c:url value='/script/database-settings.js'/>"></script>
    <script>
        $(function () { initDatabaseSettings({
            baseUrl: "<c:url value='/rest/'/>",
            csrfHeader: "${_csrf.headerName}", csrfToken: "${_csrf.token}"
        }); });
    </script>
    <style>
    </style>
</head>
<body class="mainframe bgcolor1">
<script type="text/javascript" src="<c:url value='/script/wz_tooltip.js'/>"></script>
<script type="text/javascript" src="<c:url value='/script/tip_balloon.js'/>"></script>

<c:import url="settingsHeader.jsp">
    <c:param name="cat" value="database"/>
    <c:param name="toast" value="${settings_toast}"/>
</c:import>

<p id="databaseError" class="warning" role="alert"></p>
<p id="databaseSaved" hidden><fmt:message key="common.settingssaved"/></p>
<form id="databaseSettings"><fieldset id="databaseFields" disabled>
    <p><fmt:message key="databasesettings.moreinfo"/></p>

    <table style="white-space:nowrap" class="indent">
        <tr>
            <td><fmt:message key="databasesettings.configtype"/></td>
            <td>
                <select name="configType" style="width:12em" id="configType">
                    <option value="BUILTIN">Built-in</option>
                    <option value="EXTERNAL">External</option>
                    <option value="JNDI">JNDI</option>
                </select>
                <c:import url="helpToolTip.jsp"><c:param name="topic" value="databaseConfigType"/></c:import>
            </td>
        </tr>
    </table>

    <div id="EXTERNALDatabaseOptions" class="hideawayDatabaseOptions">
            <table style="white-space:nowrap;" class="indent">
                <tr>
                    <td><fmt:message key="databasesettings.driver"/></td>
                    <td>
                        <input id="driver" name="driver" size="30"/>
                        <c:import url="helpToolTip.jsp"><c:param name="topic" value="jdbcdriver"/></c:import>
                    </td>
                </tr>
                <tr>
                    <td><fmt:message key="databasesettings.url"/></td>
                    <td>
                        <input id="url" name="url" size="58"/>
                    </td>
                </tr>
                <tr>
                    <td><fmt:message key="databasesettings.username"/></td>
                    <td>
                        <input id="username" name="username" size="36"/>
                    </td>
                </tr>
                <tr>
                    <td><fmt:message key="databasesettings.password"/></td>
                    <td>
                        <input id="password" name="password" size="36" type="password" autocomplete="new-password"/>
                        <label><input type="checkbox" name="clearPassword"/><fmt:message key="databasesettings.clearpassword"/></label>
                        <div class="detail"><fmt:message key="databasesettings.passwordunchanged"/></div>
                    </td>
                </tr>
            </table>
    </div>

    <div id="JNDIDatabaseOptions" class="hideawayDatabaseOptions">
        <table style="white-space:nowrap;" class="indent">
            <tr>
                <td><fmt:message key="databasesettings.jndiname"/></td>
                <td>
                    <input id="JNDIName" name="jndiName" size="36"/>
                    <c:import url="helpToolTip.jsp"><c:param name="topic" value="jndiname"/></c:import>
                </td>
            </tr>
        </table>
    </div>
    <div id="nonBUILTINDatabaseOptions" class="hideawayDatabaseOptions">
        <table style="white-space:nowrap" class="indent">
            <tr>
                <td><fmt:message key="databasesettings.mysqlvarcharmaxlength"/></td>
                <td>
                    <input id="mysqlVarcharMaxlength" name="mysqlVarcharMaxlength" size="8"/>
                    <c:import url="helpToolTip.jsp"><c:param name="topic" value="mysqlvarcharmaxlength"/></c:import>
                </td>
            </tr>
        </table>
        <p class="warning"><fmt:message key="databasesettings.jdbclibrary"/></p>
    </div>

    <p class="warning"><fmt:message key="databasettings.restartRequired"/></p>

  <div class="databaseBackuppable">
    <p><fmt:message key="databasesettings.autodbbackup"/></p>
    <table>
      <tr>
        <td><fmt:message key="databasesettings.dbbackupschedule"/></td>

        <td>
          <select name="dbBackupInterval" style="width:20em">
            <fmt:message key="databasesettings.interval.manually" var="never"/>
            <fmt:message key="databasesettings.interval.hourly" var="hourly"/>
            <fmt:message key="databasesettings.interval.daily" var="daily"/>
            <fmt:message key="databasesettings.interval.weekly" var="weekly"/>
            <fmt:message key="databasesettings.interval.monthly" var="monthly"/>

            <option value="-1">${never}</option>
            <option value="1">${hourly}</option>
            <option value="24">${daily}</option>
            <option value="168">${weekly}</option>
            <option value="720">${monthly}</option>
          </select>
        </td>
      </tr>
      <tr>
        <td><fmt:message key="databasesettings.keep"/></td>

        <td>
          <select name="dbBackupRetentionCount" style="width:20em">
            <fmt:message key="databasesettings.keep.all" var="all"/>
            <fmt:message key="databasesettings.keep.one" var="one"/>

            <option value="-1">${all}</option>
            <option value="1">${one}</option>

            <c:forTokens items="2 3 4 5 10 20 30 50 100 500 1000" delims=" " var="count">
                <fmt:message key="databasesettings.keep.many" var="many"><fmt:param value="${count}"/></fmt:message>
                <option value="${count}">${many}</option>
            </c:forTokens>

          </select>
        </td>
      </tr>
    </table>
  </div>
    <p>
        <input type="submit" value="<fmt:message key='common.save'/>" style="margin-right:0.3em">
        <a href="nowPlaying.view"><input type="button" value="<fmt:message key='common.cancel'/>"></a>
    </p>

</fieldset></form>

<fieldset id="databaseActions" disabled>
    <button id="backupdb" class="databaseBackuppable" type="button"><fmt:message key="databasesettings.backup"/></button>
    <button id="exportdb" type="button"><fmt:message key="databasesettings.export"/></button>
    <form id="databaseImport">
        <p class="detail"><fmt:message key="databasesettings.importlimits"/></p>
        <input type="file" name="file" accept=".xml,.zip" multiple required/>
        <button id="importdb" type="submit"><fmt:message key="databasesettings.import"/></button>
    </form>
</fieldset>
<p id="databaseOperation" role="status"></p>
</body>
</html>
