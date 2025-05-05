import { Route, BrowserRouter as Router, Routes } from 'react-router-dom';
import AboutPage from '../../pages/About/AboutPage';
import GithubAuthCallback from '../../pages/Auth/GithubCallback/GithubAuthCallback';
import SignIn from '../../pages/Auth/SignIn/SignIn';
import SignUp from '../../pages/Auth/SignUp/SignUp';
import Dashboard from '../../pages/Dashboard/Dashboard';
import HomePage from '../../pages/Home/HomePage';
import RecordingData from '../../pages/RecordingData/RecordingData';
import RecordingList from '../../pages/RecordingList/RecordingList';
import CheckoutPage from '../../pages/Subscription/CheckoutPage';
import PaymentMethodPage from '../../pages/Subscription/PaymentMethodPage';
import SubscriptionTierPage from '../../pages/Subscription/SubscriptionTierPage';
import Uploading from '../../pages/Upload/Uploading';
import UserProfile from '../../pages/UserProfile/UserProfile';
import UserProfileEdit from '../../pages/UserProfileEdit/UserProfileEdit';
import ProtectedRoute from '../common/ProtectedRoute';


function App() {
  return (
    <Router>
      <div className="min-h-screen flex flex-col">
        <Routes>
          <Route path="/" element={<HomePage />} />
          <Route path="/about" element={<AboutPage />} />
          <Route path="/signup" element={<SignUp />} />
          <Route path="/signin" element={<SignIn />} />
          <Route path="/auth/github/callback" element={<GithubAuthCallback />} />

          <Route path="/dashboard" element={
            <ProtectedRoute>
              <Dashboard />
            </ProtectedRoute>
          } />
          <Route path="/upload" element={
            <ProtectedRoute>
              <Uploading />
            </ProtectedRoute>
          } />
          <Route path="/recordings" element={
            <ProtectedRoute>
              <RecordingList />
            </ProtectedRoute>
          } />
          <Route path="/recordings/:id" element={
            <ProtectedRoute>
              <RecordingData />
            </ProtectedRoute>
          } />
          <Route path="/profile" element={
            <ProtectedRoute>
              <UserProfile />
            </ProtectedRoute>
          } />
          <Route path="/profile/edit" element={
            <ProtectedRoute>
              <UserProfileEdit />
            </ProtectedRoute>
          } />

          <Route path="/subscribe" element={
            <ProtectedRoute>
              <SubscriptionTierPage />
            </ProtectedRoute>
          } />
          <Route path="/payment" element={
            <ProtectedRoute>
              <PaymentMethodPage />
            </ProtectedRoute>
          } />
          <Route path="/checkout" element={
            <ProtectedRoute>
              <CheckoutPage />
            </ProtectedRoute>
          } />

        </Routes>
      </div>
    </Router>
  );
}

export default App;