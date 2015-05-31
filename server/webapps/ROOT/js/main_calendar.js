// Document Ready
$(function() {
  // Bind calendar creation form
  $("#calendar_create_form").submit(function(e) {
    e.preventDefault()
    submitAjaxForm($(this), function(data) { toastr.success(data.name + " created!"); refreshCalendars(); }, $("#calendar_create_errors"));
  });
  
  // Bind calendar joining form
  $("#calendar_follow_form").submit(function(e) {
    e.preventDefault()
    submitAjaxForm($(this), function(data) { toastr.success("You started following " + data.name); refreshCalendars(); }, $("#calendar_follow_errors"));
  });
  
  // Bind edit calendar buttons and forms
  $("#b_cancel_calendar").click(function() {
    $("#d_edit_calendar").toggle();
    $("#d_user_calendars").toggle();
  });
  
  $("#b_delete_calendar").click(function() {
    var calid = $(this).parent().data("calid");
    var name = $.grep(app.calendars, function(e){ return e.calendarId == calid; })[0].name;
    if(confirm("Are you sure you want to delete "+name+"?")){
      $.ajax("/api/calendar/"+calid, {
        method: "DELETE",
        success: function(data) {
          toastr.warning("Deleted " + name);
          $("#b_cancel_calendar").click(); // hide this window again
          refreshCalendars();
        },
        error: function(data) {
          toastr.error("Could not delete " + name);
          refreshCalendars();
        }
      });
    }
  });
  
  $("#user_promotion_form").submit(function(e) {
    e.preventDefault()
    submitAjaxForm($(this), function(data) { toastr.success("Updated user"); $("#b_cancel_calendar").click(); }, $("#user_promotion_errors"));
  });
  
  $("#calendar_edit_form").submit(function(e) {
    e.preventDefault()
    submitAjaxForm($(this), function(data) { toastr.success("Updated user"); $("#b_cancel_calendar").click(); }, $("#calendar_edit_errors"));
  });
  
  // Render calendar from yesterday
  updateCalendarDates(getMonday());
  
}); // End of document ready

// Update calendars
function refreshCalendars() {
  var cal_html = '<div data-calid="{{id}}" class="calendar"> \
  <div class="checkbox"> \
    <label> \
      <input type="checkbox"> {{name}} \
    </label> \
  </div> \
  <div class="calendar-extras"> \
    <p>Join code: <strong>{{joinCode}}</strong></p> \
    <p>Join enabled: <strong>{{joinEnabled}}</strong></p> \
    <button type="button" class="btn btn-info">Edit</button> \
  </div> \
</div>';
  $.get("/api/subscription/calendar", function(data) {
    app.calendars = data.calendars;
    if (data.calendars.length < 1) {
      return;
    }
    $("#d_user_calendars").empty();
    $("#select_calendar").empty();
    $.each(data.calendars, function(index, calendar) {
      $('#select_calendar')
       .append($("<option></option>")
       .attr("value",calendar.calendarId)
       .text(calendar.name));
       
      var cal_div = $(cal_html.replace("{{id}}", calendar.calendarId)
         .replace("{{name}}", calendar.name)
         .replace("{{joinCode}}", calendar.joinCode)
         .replace("{{joinEnabled}}", calendar.joinEnabled)).appendTo("#d_user_calendars");
      
      cal_div.find("input").change(refreshEvents);
      cal_div.find("button").click(function() {
        var calid = $(this).parent().parent().data("calid");
        $("#d_edit_calendar input[name='name']").val(calendar.name);
        $("#d_edit_calendar input[type='checkbox']").prop("checked", calendar.joinEnabled);
        $("#d_user_calendars").toggle();
        $("#d_edit_calendar").data("calid", calid).toggle();
      });
    });
    // Refresh events for the calendars
    $("#d_user_calendars input").first().prop("checked", "true");
    refreshEvents();
  });
}

// Update data-date field of calendar view from startDate
function updateCalendarDates(startDate) {
  // TODO: Handle different time zone
  app.current_start_date = new Date(startDate);
  $("#prev_day").next().text(formatDate(startDate));

  $("#t_calendar_body").children().each(function(k, elem) {
    // update data fields
    var date = startDate.toLocaleDateString();
    $(elem).data("date", date);

    // update heading text
    var heading = $($("#t_calendar_heading").children()[k]);
    heading.text(getWeekDay(startDate));

    // update heading class
    heading.removeClass("th_weekend").removeClass("th_weekday");
    var day = startDate.getDay();
    if (day === 0 || day === 6) {
      heading.addClass("th_weekend");
    } else {
      heading.addClass("th_weekday");
    }

    // increment date
    startDate.setDate(startDate.getDate() + 1);
  });

  $("#next_day").prev().text(formatDate(startDate));
}

// Get active_calendar ids
function getActiveCalendarIds() {
  var active_calendars = [];
  $("#calendars_collapse .calendar").each(function(index) {
    if ($(this).find("input").is(":checked")) {
      active_calendars.push($(this).data("calid"));
    }
  });
  return active_calendars;
}
