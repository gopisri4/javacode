<%-- Created by IntelliJ IDEA. --%>
<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<html>
  <head>
    <title>System Status</title>
    <link rel="stylesheet" href="https://maxcdn.bootstrapcdn.com/bootstrap/3.3.7/css/bootstrap.min.css">
    <script src="https://ajax.aspnetcdn.com/ajax/jQuery/jquery-3.3.1.min.js"></script>
    <script src="https://maxcdn.bootstrapcdn.com/bootstrap/3.3.7/js/bootstrap.min.js"></script>
  </head>
  <body>
  <table class="table">
    <thead>
    <tr>
      <th scope="col">#</th>
      <th scope="col">jobSize/times</th>
      <th scope="col">update time</th>
      <th scope="col">client IP</th>
    </tr>
    </thead>
    <tbody>
    <tr>
      <th scope="row">1</th>
      <td>Mark</td>
      <td>Otto</td>
      <td>@mdo</td>
    </tr>
    <tr>
      <th scope="row">2</th>
      <td>Jacob</td>
      <td>Thornton</td>
      <td>@fat</td>
    </tr>
    <tr>
      <th scope="row">3</th>
      <td>Larry</td>
      <td>the Bird</td>
      <td>@twitter</td>
    </tr>
    </tbody>
  </table>

  <div class="alert alert-success" role="alert">
    The Status Now is: JobSize->[${jobSize}] times/s->[${times}] QueueSize->[${queueSize}] SlowQueueSize->[${slowQueueSize}]
  </div>
  <div class="alert alert-danger" role="alert">
    ${message}
  </div>
  <div class="alert alert-warning" role="alert">
    ${jvm}
  </div>
  <div class="alert alert-info" role="alert">
    A simple info alert—check it out!
  </div>


  </body>
</html>