const bonjour = require("bonjour")();
let found = false;
const browser = bonjour.find({ type: "queryshare" }, service => {
  console.log("SERVICE_FOUND", JSON.stringify(service));
  found = true;
  browser.stop();
  process.exit(0);
});
setTimeout(() => {
  if (!found) {
    console.log("SERVICE_NOT_FOUND");
    browser.stop();
    process.exit(1);
  }
}, 5000);
