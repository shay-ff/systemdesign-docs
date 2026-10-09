// Parking Lot LLD in C++ (single-file demo)
// Compile: g++ -std=c++17 parking_lot.cpp -o parking && ./parking
#include <iostream>
#include <vector>
#include <deque>
#include <string>
#include <unordered_map>
#include <sstream>
#include <iomanip>
#include <ctime>
#include <cmath>
#include <optional>
#include <chrono>
#include <thread>
#include <stdexcept>
using namespace std;

enum class VehicleType { Motorcycle, Car, Bus };
enum class SpotType { Motorcycle, Compact, Large };

struct Vehicle {
  string license;
  VehicleType type;
};

struct Ticket {
  string ticketId;
  string license;
  VehicleType type;
  time_t entryTime;
  int levelIndex;
  SpotType spotType;
};

struct ParkingSpot {
  int spotId; // unique within level
  SpotType spotType;
  bool occupied;
  string currentLicense;
};

struct Level {
  int levelIndex;
  vector<ParkingSpot> spots;
  // For quick allocation, maintain queues of free indices by SpotType
  deque<int> freeMotorcycle;
  deque<int> freeCompact;
  deque<int> freeLarge;

  Level(int idx, int numMoto, int numCompact, int numLarge) : levelIndex(idx) {
    int id = 0;
    for (int i = 0; i < numMoto; ++i) {
      spots.push_back(ParkingSpot{id++, SpotType::Motorcycle, false, ""});
      freeMotorcycle.push_back(id - 1);
    }
    for (int i = 0; i < numCompact; ++i) {
      spots.push_back(ParkingSpot{id++, SpotType::Compact, false, ""});
      freeCompact.push_back(id - 1);
    }
    for (int i = 0; i < numLarge; ++i) {
      spots.push_back(ParkingSpot{id++, SpotType::Large, false, ""});
      freeLarge.push_back(id - 1);
    }
  }

  // Try allocate spot for a given vehicle type; returns pair(found, index)
  pair<bool, int> allocate(VehicleType type) {
    // Motorcycle fits Motorcycle->Compact->Large
    if (type == VehicleType::Motorcycle) {
      if (!freeMotorcycle.empty()) return {true, consume(freeMotorcycle)};
      if (!freeCompact.empty()) return {true, consume(freeCompact)};
      if (!freeLarge.empty()) return {true, consume(freeLarge)};
      return {false, -1};
    }
    // Car fits Compact->Large
    if (type == VehicleType::Car) {
      if (!freeCompact.empty()) return {true, consume(freeCompact)};
      if (!freeLarge.empty()) return {true, consume(freeLarge)};
      return {false, -1};
    }
    // Bus fits Large only (and one spot per bus for simplicity)
    if (type == VehicleType::Bus) {
      if (!freeLarge.empty()) return {true, consume(freeLarge)};
      return {false, -1};
    }
    return {false, -1};
  }

  void freeSpot(int spotIdx) {
    ParkingSpot &s = spots[spotIdx];
    s.occupied = false;
    s.currentLicense = "";
    switch (s.spotType) {
      case SpotType::Motorcycle: freeMotorcycle.push_back(spotIdx); break;
      case SpotType::Compact: freeCompact.push_back(spotIdx); break;
      case SpotType::Large: freeLarge.push_back(spotIdx); break;
    }
  }

 private:
  static int consume(deque<int> &dq) {
    int idx = dq.front();
    dq.pop_front();
    return idx;
  }
};

struct PricingPolicy {
  // Flat base + hourly per type (rounded up to nearest hour)
  double baseFee = 2.0;
  double perHourMotorcycle = 0.5;
  double perHourCar = 1.0;
  double perHourBus = 3.0;

  double price(VehicleType type, time_t entry, time_t exit) const {
    double hours = max(1.0, ceil(difftime(exit, entry) / 3600.0));
    double variable = 0.0;
    if (type == VehicleType::Motorcycle) variable = perHourMotorcycle;
    else if (type == VehicleType::Car) variable = perHourCar;
    else variable = perHourBus;
    return baseFee + variable * hours;
  }
};

class ParkingLot {
 public:
  ParkingLot(string name, vector<Level> levels) : name_(std::move(name)), levels_(std::move(levels)) {}

  // Returns optional ticket; nullptr if full
  unique_ptr<Ticket> park(const Vehicle &vehicle) {
    // deny duplicate active tickets
    if (licenseToTicket_.count(vehicle.license)) return nullptr;
    for (auto &level : levels_) {
      auto [ok, idx] = level.allocate(vehicle.type);
      if (!ok) continue;
      ParkingSpot &s = level.spots[idx];
      s.occupied = true;
      s.currentLicense = vehicle.license;
      auto t = make_unique<Ticket>();
      t->ticketId = generateTicketId(vehicle.license, level.levelIndex, s.spotId);
      t->license = vehicle.license;
      t->type = vehicle.type;
      t->entryTime = time(nullptr);
      t->levelIndex = level.levelIndex;
      t->spotType = s.spotType;
      licenseToTicket_[vehicle.license] = *t;
      spotKeyToLicense_[spotKey(level.levelIndex, s.spotId)] = vehicle.license;
      return t;
    }
    return nullptr;
  }

  // Returns fee charged; -1 if invalid ticket
  double unpark(const Ticket &ticket) {
    auto it = licenseToTicket_.find(ticket.license);
    if (it == licenseToTicket_.end()) return -1.0;
    // locate spot by license
    int levelIdx = ticket.levelIndex;
    optional<int> spotIdOpt = findSpotIdByLicense(ticket.license, levelIdx);
    if (!spotIdOpt.has_value()) return -1.0;
    int spotId = *spotIdOpt;
    // find spot index in level's vector
    Level &level = findLevel(levelIdx);
    int spotIdx = -1;
    for (int i = 0; i < (int)level.spots.size(); ++i) if (level.spots[i].spotId == spotId) { spotIdx = i; break; }
    if (spotIdx < 0) return -1.0;
    level.freeSpot(spotIdx);
    time_t exitTime = time(nullptr);
    double fee = pricing_.price(ticket.type, it->second.entryTime, exitTime);
    spotKeyToLicense_.erase(spotKey(levelIdx, spotId));
    licenseToTicket_.erase(it);
    return fee;
  }

  void printAvailability() const {
    cout << "Availability per level (MC/CP/LG):\n";
    for (const auto &lvl : levels_) {
      cout << "Level " << lvl.levelIndex
           << ": " << lvl.freeMotorcycle.size()
           << "/" << lvl.freeCompact.size()
           << "/" << lvl.freeLarge.size() << "\n";
    }
  }

 private:
  string name_;
  vector<Level> levels_;
  PricingPolicy pricing_{};
  unordered_map<string, Ticket> licenseToTicket_;
  unordered_map<long long, string> spotKeyToLicense_;

  static string generateTicketId(const string &license, int levelIdx, int spotId) {
    stringstream ss;
    ss << license << "-L" << levelIdx << "-S" << spotId << "-" << chrono::steady_clock::now().time_since_epoch().count();
    return ss.str();
  }

  static long long spotKey(int levelIdx, int spotId) {
    return 1LL * levelIdx * 100000 + spotId;
  }

  optional<int> findSpotIdByLicense(const string &license, int levelIdx) const {
    // Linear scan per level for simplicity in demo
    for (const auto &lvl : levels_) if (lvl.levelIndex == levelIdx) {
      for (const auto &s : lvl.spots) if (s.currentLicense == license) return s.spotId;
    }
    return nullopt;
  }

  Level &findLevel(int levelIdx) {
    for (auto &lvl : levels_) if (lvl.levelIndex == levelIdx) return lvl;
    throw runtime_error("Level not found");
  }
};

static const char *vehicleTypeStr(VehicleType t) {
  switch (t) {
    case VehicleType::Motorcycle: return "Motorcycle";
    case VehicleType::Car: return "Car";
    case VehicleType::Bus: return "Bus";
  }
  return "?";
}

int main() {
  // Create a parking lot with 2 levels
  vector<Level> levels;
  levels.emplace_back(0, /*moto*/2, /*compact*/2, /*large*/1);
  levels.emplace_back(1, /*moto*/1, /*compact*/2, /*large*/1);
  ParkingLot lot("CityCenter", std::move(levels));

  lot.printAvailability();

  Vehicle v1{"KA01AB1234", VehicleType::Car};
  Vehicle v2{"KA02ZZ9999", VehicleType::Motorcycle};
  Vehicle v3{"BUS777", VehicleType::Bus};

  auto t1 = lot.park(v1);
  auto t2 = lot.park(v2);
  auto t3 = lot.park(v3);

  cout << "Parked: " << v1.license << " as " << vehicleTypeStr(v1.type) << (t1 ? " (ok)" : " (failed)") << "\n";
  cout << "Parked: " << v2.license << " as " << vehicleTypeStr(v2.type) << (t2 ? " (ok)" : " (failed)") << "\n";
  cout << "Parked: " << v3.license << " as " << vehicleTypeStr(v3.type) << (t3 ? " (ok)" : " (failed)") << "\n";

  lot.printAvailability();

  // Simulate some time passing
  this_thread::sleep_for(chrono::milliseconds(50));

  if (t1) {
    double fee = lot.unpark(*t1);
    cout << "Unparked: " << v1.license << ", fee=$" << fixed << setprecision(2) << fee << "\n";
  }
  if (t2) {
    double fee = lot.unpark(*t2);
    cout << "Unparked: " << v2.license << ", fee=$" << fixed << setprecision(2) << fee << "\n";
  }

  lot.printAvailability();
  return 0;
}